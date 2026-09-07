package dev.kaloyanyordanov.exchange.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import org.junit.jupiter.api.Test;

class SimulatorMetricsSinkTest {

  private static OrderAccepted accepted(long id) {
    return new OrderAccepted(OrderId.of(id), Side.BUY, 100L, 5L, 1L, 0L);
  }

  private static TradeExecuted trade() {
    return new TradeExecuted(new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 1L, 2L, 0L));
  }

  @Test
  void recordsLatencyAndTradesForActiveRun() {
    RunMetrics metrics = new RunMetrics(System.nanoTime(), 100);
    metrics.markSubmit(5L, System.nanoTime() - 1_000L);
    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    sink.activate(metrics);

    sink.publish(accepted(5L));
    sink.publish(trade());

    MetricsSnapshot snapshot = metrics.snapshot(System.nanoTime());
    assertThat(snapshot.accepted()).isEqualTo(1L);
    assertThat(snapshot.trades()).isEqualTo(1L);
    assertThat(snapshot.latency().sampleCount()).isEqualTo(1L);
  }

  @Test
  void ignoresOrdersThatWereNotSubmittedByThisRun() {
    RunMetrics metrics = new RunMetrics(System.nanoTime(), 100);
    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    sink.activate(metrics);

    sink.publish(accepted(999L)); // no markSubmit -> not one of ours

    assertThat(metrics.snapshot(System.nanoTime()).accepted()).isZero();
  }

  @Test
  void withNoActiveRunItDoesNothing() {
    SimulatorMetricsSink sink = new SimulatorMetricsSink();
    // No active metrics; must not throw.
    sink.publish(accepted(1L));
    sink.publish(trade());
  }
}
