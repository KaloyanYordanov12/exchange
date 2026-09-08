package dev.kaloyanyordanov.exchange.candle;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import java.time.Instant;
import java.util.List;

/**
 * Runs the candle seed once at startup, but only when explicitly enabled. Fail-honest:
 * with seeding disabled (the default), no synthetic candles are ever created, so a
 * "real" run shows only real trades.
 */
public final class CandleSeedRunner {

  private final List<PairProperties> pairs;
  private final CandleStore store;
  private final boolean enabled;
  private final long baseSeed;

  /**
   * Creates the runner.
   *
   * @param pairs    the configured pairs
   * @param store    the candle store to seed into
   * @param enabled  whether seeding is enabled
   * @param baseSeed the reproducible RNG base seed
   */
  public CandleSeedRunner(
      List<PairProperties> pairs, CandleStore store, boolean enabled, long baseSeed) {
    this.pairs = List.copyOf(pairs);
    this.store = store;
    this.enabled = enabled;
    this.baseSeed = baseSeed;
  }

  /** Seeds pre-launch history if enabled; the launch boundary is now. */
  public void run() {
    if (enabled) {
      new CandleSeeder(baseSeed).seed(pairs, store, Instant.now());
    }
  }

  /**
   * Whether seeding is enabled.
   *
   * @return {@code true} if enabled
   */
  public boolean enabled() {
    return enabled;
  }
}
