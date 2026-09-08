package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PairCandleAggregatorTest {

  private static final Instant BASE = Instant.parse("2025-01-01T10:00:00Z");

  private final Map<String, Candle> candles = new HashMap<>();
  private final PairCandleAggregator aggregator =
      new PairCandleAggregator("BTC-USD", CandleSource.REAL, c -> candles.put(key(c), c));

  private static String key(Candle candle) {
    return candle.timeframe().label() + "@" + candle.openTime();
  }

  private Candle candle(Timeframe timeframe, Instant openTime) {
    return candles.get(timeframe.label() + "@" + openTime);
  }

  @Test
  void buildsOhlcvForOneMinuteBucketFromTrades() {
    aggregator.onTrade(BASE.plusSeconds(5), 100L, 5L);
    aggregator.onTrade(BASE.plusSeconds(20), 110L, 3L);
    aggregator.onTrade(BASE.plusSeconds(45), 90L, 2L);

    Candle m1 = candle(Timeframe.M1, BASE);
    assertThat(m1.open()).isEqualTo(100L);
    assertThat(m1.high()).isEqualTo(110L);
    assertThat(m1.low()).isEqualTo(90L);
    assertThat(m1.close()).isEqualTo(90L);
    assertThat(m1.volume()).isEqualTo(10L);
    assertThat(m1.source()).isEqualTo(CandleSource.REAL);
    assertThat(CandleInvariants.valid(m1)).isTrue();
  }

  @Test
  void largerTimeframeAggregatesConstituentMinutes() {
    // Three trades in the 10:00 minute, one in the 10:01 minute (same 5m bucket).
    aggregator.onTrade(BASE.plusSeconds(5), 100L, 5L);
    aggregator.onTrade(BASE.plusSeconds(20), 110L, 3L);
    aggregator.onTrade(BASE.plusSeconds(45), 90L, 2L);
    aggregator.onTrade(BASE.plusSeconds(70), 105L, 1L); // 10:01:10

    Candle firstMinute = candle(Timeframe.M1, BASE);
    Candle secondMinute = candle(Timeframe.M1, BASE.plusSeconds(60));
    Candle fiveMinute = candle(Timeframe.M5, BASE);

    // Both 1m candles closed correctly.
    assertThat(firstMinute.close()).isEqualTo(90L);
    assertThat(secondMinute.open()).isEqualTo(105L);
    assertThat(secondMinute.close()).isEqualTo(105L);

    // The 5m candle is the roll-up of its two minutes: first open, max high, min low,
    // last close, summed volume.
    assertThat(fiveMinute.open()).isEqualTo(100L);
    assertThat(fiveMinute.high()).isEqualTo(110L);
    assertThat(fiveMinute.low()).isEqualTo(90L);
    assertThat(fiveMinute.close()).isEqualTo(105L);
    assertThat(fiveMinute.volume()).isEqualTo(11L);
  }

  @Test
  void newDayStartsNewDailyCandle() {
    aggregator.onTrade(BASE, 100L, 1L); // 2025-01-01
    aggregator.onTrade(BASE.plusSeconds(86_400L), 200L, 2L); // 2025-01-02
    assertThat(candle(Timeframe.D1, Instant.parse("2025-01-01T00:00:00Z")).close())
        .isEqualTo(100L);
    assertThat(candle(Timeframe.D1, Instant.parse("2025-01-02T00:00:00Z")).open())
        .isEqualTo(200L);
  }
}
