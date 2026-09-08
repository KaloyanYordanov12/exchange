package dev.kaloyanyordanov.exchange.candle;

/**
 * Whether a candle was built from real trades or is labeled synthetic pre-launch
 * history. Honesty rule (section 5): seeded candles are always marked, never passed
 * off as real.
 */
public enum CandleSource {
  /** Synthetic pre-launch history (a labeled demo seed). */
  SEEDED,
  /** Built from real trades from launch forward. */
  REAL
}
