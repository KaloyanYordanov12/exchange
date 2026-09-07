package dev.kaloyanyordanov.exchange.sim;

import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A fan-out sink that records simulator metrics from the engine's event stream on
 * the matching thread. When no run is active it is a cheap no-op; during a run it
 * timestamps each {@code OrderAccepted} (the true processed instant) to measure
 * submit-to-processed latency, and counts trades. It only ever reads events — it
 * never touches the core.
 */
public final class SimulatorMetricsSink implements EventPublisher {

  private final AtomicReference<RunMetrics> active = new AtomicReference<>();

  @Override
  public void publish(EngineEvent event) {
    RunMetrics metrics = active.get();
    if (metrics == null) {
      return;
    }
    switch (event) {
      case OrderAccepted accepted -> metrics.onAccepted(accepted.id().value(), System.nanoTime());
      case TradeExecuted ignored -> metrics.onTrade();
      default -> {
        // Other events are not part of the simulator metrics.
      }
    }
  }

  void activate(RunMetrics metrics) {
    active.set(metrics);
  }
}
