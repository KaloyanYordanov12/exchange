package dev.kaloyanyordanov.exchange.invariant;

import dev.kaloyanyordanov.exchange.engine.EngineSnapshot;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the seven invariants against the live engine — on demand and
 * continuously. It obtains a consistent snapshot through the engine's real ingress
 * (never touching the core, §4.4) and runs the pure {@link InvariantChecker}. A
 * lightweight periodic check keeps a latest verdict for the public panel to read
 * cheaply; a snapshot build once per interval does not affect matching.
 */
public final class InvariantMonitor {

  private final MatchingEngine engine;
  private final Duration snapshotTimeout;
  private final long checkIntervalMillis;
  private final AtomicReference<InvariantReport> latest =
      new AtomicReference<>(InvariantReport.unavailable());
  private ScheduledExecutorService scheduler;

  /**
   * Creates the monitor.
   *
   * @param engine                the matching engine
   * @param checkIntervalMillis   the continuous check interval in milliseconds
   * @param snapshotTimeoutMillis how long to wait for each snapshot
   */
  public InvariantMonitor(
      MatchingEngine engine, long checkIntervalMillis, long snapshotTimeoutMillis) {
    if (checkIntervalMillis <= 0) {
      throw new IllegalArgumentException("check interval must be positive");
    }
    if (snapshotTimeoutMillis <= 0) {
      throw new IllegalArgumentException("snapshot timeout must be positive");
    }
    this.engine = engine;
    this.checkIntervalMillis = checkIntervalMillis;
    this.snapshotTimeout = Duration.ofMillis(snapshotTimeoutMillis);
  }

  /**
   * Runs a fresh check now, updating the latest verdict.
   *
   * @return the report (unavailable if no snapshot could be obtained)
   */
  public InvariantReport checkNow() {
    Optional<EngineSnapshot> snapshot = engine.requestSnapshot(snapshotTimeout);
    InvariantReport report =
        snapshot
            .map(state -> InvariantReport.of(InvariantChecker.check(state)))
            .orElseGet(InvariantReport::unavailable);
    latest.set(report);
    return report;
  }

  /**
   * The most recent verdict (from a continuous or on-demand check).
   *
   * @return the latest report
   */
  public InvariantReport latest() {
    return latest.get();
  }

  /** Starts the continuous checker. */
  public void start() {
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "invariant-monitor");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleAtFixedRate(
        this::checkNow, checkIntervalMillis, checkIntervalMillis, TimeUnit.MILLISECONDS);
  }

  /** Stops the continuous checker. */
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }
}
