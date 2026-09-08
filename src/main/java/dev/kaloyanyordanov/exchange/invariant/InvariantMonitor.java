package dev.kaloyanyordanov.exchange.invariant;

import dev.kaloyanyordanov.exchange.engine.EngineSnapshot;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies the invariants against the live system - on demand and continuously. It
 * obtains consistent snapshots through the engine's and cash ledger's real ingress
 * queues (never touching a core, section 4.4) and runs the pure checkers: the six
 * per-pair invariants via {@link InvariantChecker} and the two cross-account cash
 * invariants via {@link CashInvariantChecker}. A lightweight periodic check keeps a
 * latest verdict for the public panel to read cheaply.
 */
public final class InvariantMonitor {

  private final MatchingEngine engine;
  private final CashLedger cashLedger;
  private final Duration snapshotTimeout;
  private final long checkIntervalMillis;
  private final AtomicReference<InvariantReport> latest =
      new AtomicReference<>(InvariantReport.unavailable());
  private ScheduledExecutorService scheduler;

  /**
   * Creates the monitor.
   *
   * @param engine                the matching engine
   * @param cashLedger            the shared cash ledger
   * @param checkIntervalMillis   the continuous check interval in milliseconds
   * @param snapshotTimeoutMillis how long to wait for each snapshot
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "the engine and cash ledger are shared singleton services; the monitor reads their"
              + " snapshots through their real ingress and stores the shared references by design")
  public InvariantMonitor(
      MatchingEngine engine,
      CashLedger cashLedger,
      long checkIntervalMillis,
      long snapshotTimeoutMillis) {
    if (checkIntervalMillis <= 0) {
      throw new IllegalArgumentException("check interval must be positive");
    }
    if (snapshotTimeoutMillis <= 0) {
      throw new IllegalArgumentException("snapshot timeout must be positive");
    }
    this.engine = engine;
    this.cashLedger = cashLedger;
    this.checkIntervalMillis = checkIntervalMillis;
    this.snapshotTimeout = Duration.ofMillis(snapshotTimeoutMillis);
  }

  /**
   * Runs a fresh check now, updating the latest verdict.
   *
   * @return the report (unavailable if any snapshot could not be obtained)
   */
  public InvariantReport checkNow() {
    Optional<EngineSnapshot> engineSnapshot = engine.requestSnapshot(snapshotTimeout);
    Optional<CashSnapshot> cashSnapshot = cashLedger.snapshot(snapshotTimeout);
    if (engineSnapshot.isEmpty() || cashSnapshot.isEmpty()) {
      InvariantReport unavailable = InvariantReport.unavailable();
      latest.set(unavailable);
      return unavailable;
    }
    List<InvariantResult> results = new ArrayList<>();
    results.addAll(InvariantChecker.check(engineSnapshot.get()).results());
    results.addAll(CashInvariantChecker.check(cashSnapshot.get()).results());
    InvariantReport report = InvariantReport.of(CheckReport.of(results));
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
