package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleSeederTest {

  private static final Instant LAUNCH = Instant.parse("2025-06-01T00:00:00Z");
  private static final Instant FAR_FUTURE = Instant.ofEpochSecond(4_102_444_800L);
  // One large-magnitude pair (BTC ~ 4e10) and one small (DOGE ~ 8e4), to prove the
  // scaled-integer candle math is correct across magnitudes.
  private static final List<PairProperties> PAIRS =
      List.of(
          new PairProperties("BTC", "USD", 10_000L, 1L, 40_000_000_000L),
          new PairProperties("DOGE", "USD", 1L, 1L, 80_000L));

  @Test
  void seededCandlesAreValidLabeledAndBeforeLaunch() {
    CandleStore store = new CandleStore();
    new CandleSeeder(42L).seed(PAIRS, store, LAUNCH);

    for (PairProperties pair : PAIRS) {
      for (Timeframe timeframe : Timeframe.values()) {
        List<Candle> series = store.query(pair.pairId(), timeframe, Instant.EPOCH, LAUNCH);
        assertThat(series).as("%s %s history", pair.pairId(), timeframe).isNotEmpty();
        assertThat(CandleInvariants.sequenceValid(series)).isTrue();
        assertThat(series)
            .allSatisfy(
                candle -> {
                  assertThat(CandleInvariants.valid(candle)).isTrue();
                  assertThat(candle.source()).isEqualTo(CandleSource.SEEDED);
                  assertThat(candle.openTime()).isBefore(LAUNCH);
                  assertThat(candle.low()).isPositive();
                  assertThat(candle.open() % pair.tickSize()).isZero(); // tick-aligned
                });
      }
    }
  }

  @Test
  void seededHistoryIsReproducibleForSeed() {
    CandleStore a = new CandleStore();
    CandleStore b = new CandleStore();
    new CandleSeeder(7L).seed(PAIRS, a, LAUNCH);
    new CandleSeeder(7L).seed(PAIRS, b, LAUNCH);
    assertThat(a.query("BTC-USD", Timeframe.D1, Instant.EPOCH, LAUNCH))
        .isEqualTo(b.query("BTC-USD", Timeframe.D1, Instant.EPOCH, LAUNCH));
  }

  @Test
  void realTradesAppendAfterTheSeededBoundary() {
    CandleStore store = new CandleStore();
    new CandleSeeder(42L).seed(PAIRS, store, LAUNCH);

    // A real trade at launch produces a REAL candle after all the seeded history.
    Candle real =
        new Candle(
            "DOGE-USD", Timeframe.D1, LAUNCH, 80_000L, 81_000L, 79_000L, 80_500L, 42L,
            CandleSource.REAL);
    store.upsert(real);

    assertThat(store.realBoundary("DOGE-USD", Timeframe.D1)).contains(LAUNCH);
    List<Candle> all = store.query("DOGE-USD", Timeframe.D1, Instant.EPOCH, FAR_FUTURE);
    assertThat(all.get(all.size() - 1).source()).isEqualTo(CandleSource.REAL);
    assertThat(all).filteredOn(c -> c.source() == CandleSource.SEEDED).isNotEmpty();
  }
}
