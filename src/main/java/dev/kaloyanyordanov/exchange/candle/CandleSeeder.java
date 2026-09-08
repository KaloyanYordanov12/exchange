package dev.kaloyanyordanov.exchange.candle;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Backfills labeled synthetic pre-launch history so the demo's long timeframes are not
 * empty on day one. For each pair and timeframe it generates a deterministic random
 * walk from the pair's reference price, ending just before the launch boundary, and
 * upserts the candles as {@link CandleSource#SEEDED}. Seeded candles are valid candles
 * (they satisfy every M2 invariant); they are always marked SEEDED and never passed off
 * as real (section 5). Real trades from launch forward append to the same series as
 * {@link CandleSource#REAL}.
 *
 * <p>Seeding is a demo aid and is off unless explicitly enabled, so a "real" run never
 * shows accidental synthetic data.
 */
public final class CandleSeeder {

  /** How many candles of history to seed per timeframe. */
  private static final Map<Timeframe, Integer> COUNTS = new EnumMap<>(Timeframe.class);

  static {
    COUNTS.put(Timeframe.M1, 240); // 4 hours
    COUNTS.put(Timeframe.M5, 288); // 1 day
    COUNTS.put(Timeframe.M15, 192); // 2 days
    COUNTS.put(Timeframe.H1, 168); // 1 week
    COUNTS.put(Timeframe.H4, 180); // 30 days
    COUNTS.put(Timeframe.D1, 730); // 2 years
    COUNTS.put(Timeframe.W1, 156); // 3 years
    COUNTS.put(Timeframe.MO1, 36); // 3 years
  }

  private final long baseSeed;
  // One reusable generator, re-seeded deterministically per series (a fresh Random per
  // series would be flagged as wasteful and gives no better randomness).
  private final Random rng = new Random();

  /**
   * Creates a seeder.
   *
   * @param baseSeed the base RNG seed (makes the seeded history reproducible)
   */
  public CandleSeeder(long baseSeed) {
    this.baseSeed = baseSeed;
  }

  /**
   * Seeds pre-launch history for every pair and timeframe into the store.
   *
   * @param pairs  the configured pairs (for their reference prices and scaling)
   * @param store  the candle store to upsert into
   * @param launch the launch boundary; seeded candles all fall before it
   */
  public void seed(List<PairProperties> pairs, CandleStore store, Instant launch) {
    for (PairProperties pair : pairs) {
      for (Timeframe timeframe : Timeframe.values()) {
        seedSeries(pair, timeframe, store, launch, COUNTS.getOrDefault(timeframe, 0));
      }
    }
  }

  private void seedSeries(
      PairProperties pair, Timeframe timeframe, CandleStore store, Instant launch, int count) {
    rng.setSeed(baseSeed * 1_000_003L + pair.pairId().hashCode() * 31L + timeframe.ordinal());
    long tick = pair.tickSize();
    long maxTicks = Math.max(1L, pair.referencePrice() / (50L * tick)); // ~2% swings
    int moveBound = (int) Math.min(maxTicks, Integer.MAX_VALUE / 2);
    int spanBound = (int) Math.min(maxTicks, Integer.MAX_VALUE - 1);
    long price = pair.referencePrice();
    Instant bucket = timeframe.advance(timeframe.bucketStart(launch), -count);
    for (int i = 0; i < count; i++) {
      long open = price;
      long moveTicks = rng.nextInt(moveBound * 2 + 1) - moveBound;
      long close = alignedPositive(open + moveTicks * tick, tick);
      long high = Math.max(open, close) + (long) rng.nextInt(spanBound + 1) * tick;
      long low = Math.min(open, close) - (long) rng.nextInt(spanBound + 1) * tick;
      if (low < tick) {
        low = tick;
      }
      long volume = 1L + rng.nextInt(1_000);
      store.upsert(
          new Candle(
              pair.pairId(), timeframe, bucket, open, high, low, close, volume,
              CandleSource.SEEDED));
      price = close;
      bucket = timeframe.advance(bucket, 1);
    }
  }

  private static long alignedPositive(long price, long tick) {
    long aligned = price - Math.floorMod(price, tick);
    return aligned < tick ? tick : aligned;
  }
}
