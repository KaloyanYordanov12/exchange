package dev.kaloyanyordanov.exchange.candle;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A candle timeframe and the rule for the bucket a trade time falls into. Fixed
 * timeframes (1m..4h, 1d, 1w) floor to a fixed grid in UTC; the monthly timeframe
 * uses the calendar month. The "1y" view in the UI is a query range over daily
 * candles, not a stored timeframe.
 */
public enum Timeframe {
  /** One minute. */
  M1("1m", 60L),
  /** Five minutes. */
  M5("5m", 300L),
  /** Fifteen minutes. */
  M15("15m", 900L),
  /** One hour. */
  H1("1h", 3_600L),
  /** Four hours. */
  H4("4h", 14_400L),
  /** One day (UTC). */
  D1("1d", 86_400L),
  /** One week (a fixed 7-day UTC grid). */
  W1("1w", 604_800L),
  /** One calendar month (UTC). */
  MO1("1M", 0L);

  private final String label;
  private final long seconds;

  Timeframe(String label, long seconds) {
    this.label = label;
    this.seconds = seconds;
  }

  /**
   * The UI/API label, e.g. {@code 1m} or {@code 1M}.
   *
   * @return the label
   */
  public String label() {
    return label;
  }

  /**
   * Resolves a label (case-sensitive, e.g. {@code 1h}) to a timeframe.
   *
   * @param label the label
   * @return the timeframe, or {@code null} if unknown
   */
  public static Timeframe fromLabel(String label) {
    for (Timeframe timeframe : values()) {
      if (timeframe.label.equals(label)) {
        return timeframe;
      }
    }
    return null;
  }

  /**
   * The start instant of the bucket that {@code time} belongs to.
   *
   * @param time the trade time
   * @return the bucket's open time
   */
  public Instant bucketStart(Instant time) {
    return switch (this) {
      case M1, M5, M15, H1, H4, W1 -> {
        long epoch = time.getEpochSecond();
        yield Instant.ofEpochSecond(Math.floorDiv(epoch, seconds) * seconds);
      }
      case D1 -> time.truncatedTo(ChronoUnit.DAYS);
      case MO1 ->
          time.atZone(ZoneOffset.UTC)
              .toLocalDate()
              .withDayOfMonth(1)
              .atStartOfDay(ZoneOffset.UTC)
              .toInstant();
    };
  }
}
