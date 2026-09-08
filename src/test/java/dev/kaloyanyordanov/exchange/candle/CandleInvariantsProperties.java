package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The candle invariants, property-tested over random trade streams: every candle is
 * valid ({@code high >= max(open,close)}, {@code low <= min(open,close)},
 * {@code low <= high}, volume equals the summed quantities), each timeframe's sequence
 * is gap/overlap-free and aligned, and every larger-timeframe candle exactly rolls up
 * its constituent 1m base candles. A corruption case confirms the checker rejects a
 * bad candle rather than only ever passing.
 */
class CandleInvariantsProperties {

  private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

  record Trade(long deltaSeconds, long price, long quantity) {}

  @Provide
  Arbitrary<List<Trade>> tradeStreams() {
    Arbitrary<Trade> trade =
        Combinators.combine(
                Arbitraries.longs().between(0L, 4_000L),
                Arbitraries.longs().between(1L, 1_000L),
                Arbitraries.longs().between(1L, 100L))
            .as(Trade::new);
    return trade.list().ofMinSize(1).ofMaxSize(60);
  }

  @Property(tries = 40)
  void aggregationHoldsEveryCandleInvariant(@ForAll("tradeStreams") List<Trade> trades) {
    Map<String, Candle> candles = new HashMap<>();
    PairCandleAggregator aggregator =
        new PairCandleAggregator(
            "BTC-USD",
            CandleSource.REAL,
            candle -> candles.put(candle.timeframe().label() + "@" + candle.openTime(), candle));

    long totalVolume = 0L;
    long time = 0L;
    for (Trade trade : trades) {
      time += trade.deltaSeconds();
      aggregator.onTrade(BASE.plusSeconds(time), trade.price(), trade.quantity());
      totalVolume += trade.quantity();
    }

    List<Candle> all = new ArrayList<>(candles.values());
    // 1) Every candle is internally valid.
    assertThat(all).allSatisfy(candle -> assertThat(CandleInvariants.valid(candle)).isTrue());

    // 2) Each timeframe's sequence is well-formed, and its volume sums to all trades.
    for (Timeframe timeframe : Timeframe.values()) {
      List<Candle> series =
          all.stream()
              .filter(candle -> candle.timeframe() == timeframe)
              .sorted(Comparator.comparing(Candle::openTime))
              .toList();
      assertThat(CandleInvariants.sequenceValid(series)).isTrue();
      assertThat(series.stream().mapToLong(Candle::volume).sum()).isEqualTo(totalVolume);
    }

    // 3) Every 5m candle exactly rolls up its constituent 1m base candles.
    List<Candle> minutes =
        all.stream().filter(candle -> candle.timeframe() == Timeframe.M1).toList();
    for (Candle five : all) {
      if (five.timeframe() != Timeframe.M5) {
        continue;
      }
      List<Candle> constituents =
          minutes.stream()
              .filter(m -> Timeframe.M5.bucketStart(m.openTime()).equals(five.openTime()))
              .sorted(Comparator.comparing(Candle::openTime))
              .toList();
      assertThat(five.open()).isEqualTo(constituents.get(0).open());
      assertThat(five.close()).isEqualTo(constituents.get(constituents.size() - 1).close());
      assertThat(five.high())
          .isEqualTo(constituents.stream().mapToLong(Candle::high).max().orElseThrow());
      assertThat(five.low())
          .isEqualTo(constituents.stream().mapToLong(Candle::low).min().orElseThrow());
      assertThat(five.volume())
          .isEqualTo(constituents.stream().mapToLong(Candle::volume).sum());
    }
  }

  @Property(tries = 20)
  void detectsCorruptCandles(
      @ForAll("tradeStreams") List<Trade> ignored, @ForAll long noise) {
    // A candle whose high is below its open is invalid, whatever the noise.
    Candle corrupt =
        new Candle(
            "BTC-USD", Timeframe.M1, BASE, 100L, 90L, 50L, 80L, Math.abs(noise) + 1L,
            CandleSource.REAL);
    assertThat(CandleInvariants.valid(corrupt)).isFalse();
  }
}
