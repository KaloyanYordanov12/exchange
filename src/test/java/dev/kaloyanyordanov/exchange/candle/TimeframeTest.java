package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeframeTest {

  private static Instant at(String iso) {
    return Instant.parse(iso);
  }

  @Test
  void fixedTimeframesFloorToTheirGrid() {
    assertThat(Timeframe.M1.bucketStart(at("2025-01-01T10:00:45Z")))
        .isEqualTo(at("2025-01-01T10:00:00Z"));
    assertThat(Timeframe.M5.bucketStart(at("2025-01-01T10:07:30Z")))
        .isEqualTo(at("2025-01-01T10:05:00Z"));
    assertThat(Timeframe.M15.bucketStart(at("2025-01-01T10:47:00Z")))
        .isEqualTo(at("2025-01-01T10:45:00Z"));
    assertThat(Timeframe.H1.bucketStart(at("2025-01-01T10:30:00Z")))
        .isEqualTo(at("2025-01-01T10:00:00Z"));
    assertThat(Timeframe.H4.bucketStart(at("2025-01-01T14:00:00Z")))
        .isEqualTo(at("2025-01-01T12:00:00Z"));
  }

  @Test
  void dailyFloorsToUtcMidnight() {
    assertThat(Timeframe.D1.bucketStart(at("2025-01-01T23:59:59Z")))
        .isEqualTo(at("2025-01-01T00:00:00Z"));
  }

  @Test
  void weeklyIsConsistentSevenDayGrid() {
    Instant bucket = Timeframe.W1.bucketStart(at("2025-03-12T09:00:00Z"));
    assertThat(Timeframe.W1.bucketStart(bucket)).isEqualTo(bucket); // aligned / idempotent
    // Every instant within the 7-day window maps to the same bucket start...
    assertThat(Timeframe.W1.bucketStart(bucket.plusSeconds(604_800L - 1L))).isEqualTo(bucket);
    // ...and the next second starts the next bucket.
    assertThat(Timeframe.W1.bucketStart(bucket.plusSeconds(604_800L)))
        .isEqualTo(bucket.plusSeconds(604_800L));
  }

  @Test
  void monthlyFloorsToTheFirstOfTheMonth() {
    assertThat(Timeframe.MO1.bucketStart(at("2025-03-15T12:00:00Z")))
        .isEqualTo(at("2025-03-01T00:00:00Z"));
    assertThat(Timeframe.MO1.bucketStart(at("2025-12-31T23:59:59Z")))
        .isEqualTo(at("2025-12-01T00:00:00Z"));
  }

  @Test
  void labelsRoundTrip() {
    for (Timeframe timeframe : Timeframe.values()) {
      assertThat(Timeframe.fromLabel(timeframe.label())).isEqualTo(timeframe);
    }
    assertThat(Timeframe.H1.label()).isEqualTo("1h");
    assertThat(Timeframe.MO1.label()).isEqualTo("1M");
    assertThat(Timeframe.fromLabel("nope")).isNull();
  }
}
