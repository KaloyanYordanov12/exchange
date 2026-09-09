package dev.kaloyanyordanov.exchange.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class LoadSimulatorTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  /** A started cash ledger plus an asset-backed engine over accounts 1 and 2. */
  private record Setup(MatchingEngine engine, CashLedger cash) {}

  private static Setup setup(int engineCapacity, EventPublisher publisher) {
    CashLedger cash = new CashLedger(1 << 16);
    cash.start();
    AssetLedger asset = new AssetLedger();
    asset.endow(1L, 1_000_000L);
    asset.endow(2L, 1_000_000L);
    cash.deposit(1L, 1_000_000_000L, "seed");
    cash.deposit(2L, 1_000_000_000L, "seed");
    MatchingEngine engine = new MatchingEngine(SYMBOL, engineCapacity, publisher, asset, cash);
    return new Setup(engine, cash);
  }

  private static SimulatorConfig config(int traders, int perTrader) {
    return new SimulatorConfig(
        traders, perTrader, 0L, 60_000L, 100L, 5L, 1L, 5L, List.of(1L, 2L), 7L, 1_000_000, 0L, 0L);
  }

  @Test
  void drivesTheRealEngineAndMeasuresRealMetrics() throws InterruptedException {
    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    Setup setup = setup(1 << 16, sink);
    setup.engine().start();
    LoadSimulator simulator = new LoadSimulator(setup.engine(), SYMBOL, setup.cash(), sink);

    simulator.start(config(4, 25)); // 100 orders total

    await().atMost(Duration.ofSeconds(20)).until(() -> !simulator.isRunning());
    await().atMost(Duration.ofSeconds(20)).until(() -> simulator.metrics().accepted() == 100L);
    setup.engine().stop();
    setup.cash().stop();

    MetricsSnapshot metrics = simulator.metrics();
    assertThat(metrics.submitted()).isEqualTo(100L);
    assertThat(metrics.accepted()).isEqualTo(100L);
    assertThat(metrics.rejected()).isZero();
    assertThat(metrics.trades()).isPositive(); // the book actually trades
    assertThat(metrics.latency().sampleCount()).isEqualTo(100L);
    assertThat(metrics.throughputPerSecond()).isPositive();
  }

  @Test
  void rejectsSecondRunWhileOneIsActive() throws InterruptedException {
    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    Setup setup = setup(1 << 16, sink);
    setup.engine().start();
    LoadSimulator simulator = new LoadSimulator(setup.engine(), SYMBOL, setup.cash(), sink);

    // A long-paced run stays active while we try to start another.
    simulator.start(
        new SimulatorConfig(
            2, 1_000, 5L, 60_000L, 100L, 5L, 1L, 5L, List.of(1L, 2L), 7L, 1_000_000, 0L, 0L));
    try {
      assertThatThrownBy(() -> simulator.start(config(1, 1)))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      simulator.stop();
      setup.engine().stop();
      setup.cash().stop();
    }
  }

  @Test
  void saturatedEngineRecordsBackpressureRejectionsHonestly() throws InterruptedException {
    // A publisher that blocks the matching thread so the tiny queue fills.
    CountDownLatch gate = new CountDownLatch(1);
    EventPublisher blocking =
        (EngineEvent event) -> {
          try {
            gate.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    Setup setup = setup(8, blocking);
    setup.engine().start();

    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    LoadSimulator simulator = new LoadSimulator(setup.engine(), SYMBOL, setup.cash(), sink);
    simulator.start(config(1, 500)); // one trader floods a queue of capacity 8

    await().atMost(Duration.ofSeconds(20)).until(() -> !simulator.isRunning());
    MetricsSnapshot metrics = simulator.metrics();

    // Back-pressure surfaced honestly: some enqueued, the rest rejected; never bypassed.
    assertThat(metrics.rejected()).isPositive();
    assertThat(metrics.submitted() + metrics.rejected()).isEqualTo(500L);
    assertThat(metrics.submitted()).isLessThanOrEqualTo(9L); // capacity 8 (+1 in flight)

    gate.countDown();
    setup.engine().stop();
    setup.cash().stop();
  }
}
