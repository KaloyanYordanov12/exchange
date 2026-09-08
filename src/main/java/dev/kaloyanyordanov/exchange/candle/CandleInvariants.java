package dev.kaloyanyordanov.exchange.candle;

import java.util.List;

/**
 * The candle correctness invariants, as pure predicates so they can be property-
 * tested and used to reject corrupted candles (section 5). A candle that could only
 * ever be called "valid" would be worthless, so these are proven to reject bad
 * candles, not only to accept good ones.
 */
public final class CandleInvariants {

  private CandleInvariants() {}

  /**
   * Whether a single candle is internally valid: {@code high >= max(open, close)},
   * {@code low <= min(open, close)}, {@code low <= high}, and {@code volume > 0}.
   *
   * @param candle the candle
   * @return {@code true} if valid
   */
  public static boolean valid(Candle candle) {
    return candle.high() >= Math.max(candle.open(), candle.close())
        && candle.low() <= Math.min(candle.open(), candle.close())
        && candle.low() <= candle.high()
        && candle.volume() > 0;
  }

  /**
   * Whether a candle sequence is well-formed: all the same pair and timeframe, each
   * open time aligned to that timeframe's bucket grid, and strictly increasing open
   * times (no duplicate buckets, no overlaps; sparse gaps from empty buckets are
   * allowed).
   *
   * @param candles the candles, in order
   * @return {@code true} if the sequence is well-formed
   */
  public static boolean sequenceValid(List<Candle> candles) {
    for (int i = 0; i < candles.size(); i++) {
      Candle candle = candles.get(i);
      if (!candle.timeframe().bucketStart(candle.openTime()).equals(candle.openTime())) {
        return false; // not aligned to the bucket grid
      }
      if (i > 0) {
        Candle previous = candles.get(i - 1);
        if (!previous.timeframe().equals(candle.timeframe())
            || !previous.pairId().equals(candle.pairId())
            || !candle.openTime().isAfter(previous.openTime())) {
          return false; // mixed series, or not strictly increasing
        }
      }
    }
    return true;
  }
}
