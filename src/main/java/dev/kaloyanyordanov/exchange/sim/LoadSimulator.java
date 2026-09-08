package dev.kaloyanyordanov.exchange.sim;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import dev.kaloyanyordanov.exchange.sim.OrderGenerator.GeneratedOrder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * In-process, virtual-thread-per-trader load generator. Traders submit through
 * the <em>real</em> ingress ({@link MatchingEngine#submit}) exactly like the API,
 * take the real back-pressure rejection when the queue is full, and never bypass
 * the queue or reach into the core. All reported metrics are measured, not
 * fabricated.
 */
public final class LoadSimulator {

  // Simulator order ids live in a high range to avoid colliding with API ids.
  private static final long ORDER_ID_BASE = 1_000_000_000L;
  private static final Duration RESERVE_TIMEOUT = Duration.ofSeconds(1);

  private final MatchingEngine engine;
  private final Symbol symbol;
  private final CashLedger cashLedger;
  private final SimulatorMetricsSink sink;
  private final AtomicLong orderIds;
  private final Object lifecycle = new Object();

  private volatile RunMetrics metrics;
  private volatile boolean stopRequested;
  private ExecutorService executor;

  /**
   * Creates the simulator.
   *
   * @param engine     the matching engine to drive through its real ingress
   * @param symbol     the traded symbol (tick/lot for order generation)
   * @param cashLedger the shared cash ledger, for reserving a buy's cash like the API
   * @param sink       the metrics sink that records processed events
   */
  public LoadSimulator(
      MatchingEngine engine, Symbol symbol, CashLedger cashLedger, SimulatorMetricsSink sink) {
    this(engine, symbol, cashLedger, sink, ORDER_ID_BASE);
  }

  /**
   * Creates a simulator whose order ids start at {@code orderIdBase}, so a per-pair
   * simulator's ids stay in a distinct range for shared persistence.
   *
   * @param engine      the matching engine to drive through its real ingress
   * @param symbol      the traded symbol (tick/lot for order generation)
   * @param cashLedger  the shared cash ledger, for reserving a buy's cash like the API
   * @param sink        the metrics sink that records processed events
   * @param orderIdBase the first order id this simulator assigns
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "the engine and cash ledger are shared singleton services the simulator drives through"
              + " their real ingress; storing the shared references is the intended design")
  public LoadSimulator(
      MatchingEngine engine,
      Symbol symbol,
      CashLedger cashLedger,
      SimulatorMetricsSink sink,
      long orderIdBase) {
    this.engine = engine;
    this.symbol = symbol;
    this.cashLedger = cashLedger;
    this.sink = sink;
    this.orderIds = new AtomicLong(orderIdBase);
  }

  /**
   * Whether a run is currently in progress.
   *
   * @return {@code true} if running
   */
  public boolean isRunning() {
    RunMetrics current = metrics;
    return current != null && current.isRunning();
  }

  /**
   * Starts a run. Only one run may be active at a time.
   *
   * @param config the run configuration
   */
  public void start(SimulatorConfig config) {
    synchronized (lifecycle) {
      if (isRunning()) {
        throw new IllegalStateException("a simulation is already running");
      }
      stopRequested = false;
      long startNanos = System.nanoTime();
      RunMetrics runMetrics = new RunMetrics(startNanos, config.maxLatencySamples());
      metrics = runMetrics;
      sink.activate(runMetrics);
      executor = Executors.newVirtualThreadPerTaskExecutor();

      long deadlineNanos = startNanos + config.maxDurationMillis() * 1_000_000L;
      CountDownLatch done = new CountDownLatch(config.traderCount());
      for (int i = 0; i < config.traderCount(); i++) {
        long account = config.accountIds().get(i % config.accountIds().size());
        long seed = config.randomSeed() + i;
        executor.execute(
            () -> {
              try {
                runTrader(config, runMetrics, account, seed, deadlineNanos);
              } finally {
                done.countDown();
              }
            });
      }
      Thread.ofVirtual()
          .name("sim-coordinator")
          .start(
              () -> {
                awaitQuietly(done); // traders stopped submitting
                finish(runMetrics); // close the submission window
              });
    }
  }

  private void runTrader(
      SimulatorConfig config, RunMetrics runMetrics, long account, long seed, long deadlineNanos) {
    OrderGenerator generator = new OrderGenerator(symbol, config, new Random(seed));
    long pacingNanos =
        config.orderRatePerSecond() > 0 ? 1_000_000_000L / config.orderRatePerSecond() : 0L;
    for (int j = 0;
        j < config.ordersPerTrader() && !stopRequested && System.nanoTime() < deadlineNanos;
        j++) {
      GeneratedOrder order = generator.next();
      long id = orderIds.getAndIncrement();
      // A buy must reserve its cash first, exactly like the API gateway does, so an
      // unfunded buy is rejected rather than driving the account's cash negative.
      long reserved = 0L;
      if (order.side() == Side.BUY) {
        reserved = Math.multiplyExact(order.price(), order.quantity());
        if (cashLedger.reserve(account, reserved, RESERVE_TIMEOUT) != ReservationOutcome.RESERVED) {
          runMetrics.countRejected();
          continue;
        }
      }
      runMetrics.markSubmit(id, System.nanoTime());
      SubmitResult result =
          engine.submit(
              new SubmitOrder(
                  OrderId.of(id), order.side(), order.price(), order.quantity(), account));
      if (result == SubmitResult.ENQUEUED) {
        runMetrics.countSubmitted();
      } else {
        if (order.side() == Side.BUY) {
          cashLedger.release(account, reserved);
        }
        runMetrics.countRejected();
        runMetrics.unmark(id);
      }
      if (pacingNanos > 0L) {
        LockSupport.parkNanos(pacingNanos);
      }
    }
  }

  // The submission window is closed; the sink keeps counting the engine's drain
  // until the next run replaces the active metrics.
  private void finish(RunMetrics runMetrics) {
    runMetrics.finish(System.nanoTime());
    synchronized (lifecycle) {
      if (executor != null) {
        executor.shutdown();
      }
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /** Requests the current run to stop; traders finish promptly. */
  public void stop() {
    synchronized (lifecycle) {
      stopRequested = true;
    }
  }

  /**
   * The latest metrics for the current or most recent run.
   *
   * @return the metrics snapshot
   */
  public MetricsSnapshot metrics() {
    RunMetrics current = metrics;
    return current == null ? MetricsSnapshot.EMPTY : current.snapshot(System.nanoTime());
  }
}
