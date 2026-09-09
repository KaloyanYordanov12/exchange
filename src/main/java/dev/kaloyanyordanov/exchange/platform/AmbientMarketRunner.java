package dev.kaloyanyordanov.exchange.platform;

import dev.kaloyanyordanov.exchange.sim.SimulatorConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-starts a small, always-on ambient market at boot so the exchange looks alive
 * without anyone pressing Launch. It spreads {@code ambientTraders} across the pairs
 * and starts one continuous, human-paced, mostly-passive simulator run per pair
 * through the existing {@link ExchangeRegistry}, so the book fills two-sided and
 * trickles trades. It is a thin orchestrator over the load simulator; it never
 * touches the matching core.
 *
 * <p>Lightweight by construction: a low trader count with multi-second think-time
 * keeps the order rate small. Each pair's share is clamped to the public trader cap.
 * Off by default ({@code ambientTraders == 0}).
 */
public final class AmbientMarketRunner {

  private static final long MIN_THINK_MILLIS = 2_000L;
  private static final long MAX_THINK_MILLIS = 8_000L;
  private static final double AGGRESSION = 0.30;
  private static final int MAX_LATENCY_SAMPLES = 10_000;
  private static final long SPREAD_DIVISOR = 400L; // ~0.25% half-band around the reference

  private final ExchangeRegistry registry;
  private final List<Long> accountIds;
  private final int ambientTraders;
  private final int publicMaxTraders;
  private final long baseSeed;
  private final List<String> startedPairs = new ArrayList<>();

  /**
   * Creates the runner.
   *
   * @param registry         the pair registry to drive
   * @param accountIds       the funded accounts the ambient traders act on
   * @param ambientTraders   total ambient traders across all pairs (0 = off)
   * @param publicMaxTraders the public trader cap each pair's share is clamped to
   * @param baseSeed         base RNG seed (reproducible ambient flow)
   */
  public AmbientMarketRunner(
      ExchangeRegistry registry,
      List<Long> accountIds,
      int ambientTraders,
      int publicMaxTraders,
      long baseSeed) {
    this.registry = registry;
    this.accountIds = List.copyOf(accountIds);
    this.ambientTraders = ambientTraders;
    this.publicMaxTraders = publicMaxTraders;
    this.baseSeed = baseSeed;
  }

  /** Starts the ambient run, spreading traders across the pairs. A no-op when off. */
  public void start() {
    if (ambientTraders <= 0 || accountIds.isEmpty()) {
      return;
    }
    List<PairInfo> pairs = registry.pairs();
    if (pairs.isEmpty()) {
      return;
    }
    int n = pairs.size();
    int base = ambientTraders / n;
    int remainder = ambientTraders % n;
    for (int i = 0; i < n; i++) {
      PairInfo pair = pairs.get(i);
      int share = base + (i < remainder ? 1 : 0);
      int traders = Math.min(share, publicMaxTraders);
      if (traders <= 0) {
        continue;
      }
      try {
        registry.startSimulator(pair.pairId(), configFor(pair, traders, baseSeed + i));
        startedPairs.add(pair.pairId());
      } catch (RuntimeException alreadyRunning) {
        // A run is already active on this pair (e.g. a restart); leave it be.
      }
    }
  }

  /** Stops every ambient run this runner started. */
  public void stop() {
    for (String pairId : startedPairs) {
      try {
        registry.stopSimulator(pairId);
      } catch (RuntimeException ignored) {
        // Best-effort on shutdown.
      }
    }
    startedPairs.clear();
  }

  /**
   * The number of pairs an ambient run was started on (observability/tests).
   *
   * @return the started-pair count
   */
  public int startedCount() {
    return startedPairs.size();
  }

  private SimulatorConfig configFor(PairInfo pair, int traders, long seed) {
    long priceInTicks = Math.max(1L, pair.referencePrice() / pair.tickSize());
    long spreadTicks = Math.max(10L, priceInTicks / SPREAD_DIVISOR);
    return new SimulatorConfig(
        traders,
        0, // unbounded orders
        0L, // no fixed rate; think-time paces
        0L, // continuous: run until stopped
        pair.referencePrice(),
        spreadTicks,
        1L,
        5L,
        accountIds,
        seed,
        MAX_LATENCY_SAMPLES,
        MIN_THINK_MILLIS,
        MAX_THINK_MILLIS,
        AGGRESSION);
  }
}
