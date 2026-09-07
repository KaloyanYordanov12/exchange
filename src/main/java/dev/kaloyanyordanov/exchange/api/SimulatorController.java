package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.MetricsSnapshot;
import dev.kaloyanyordanov.exchange.sim.SimulatorConfig;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-gated control surface for the load simulator so the UI's simulator panel
 * can start a run, stop it, and read live, real metrics.
 */
@RestController
public class SimulatorController {

  private final LoadSimulator simulator;
  private final List<Long> accountIds;

  /**
   * Creates the controller.
   *
   * @param simulator  the load simulator
   * @param properties the exchange configuration (for the funded account ids)
   */
  public SimulatorController(LoadSimulator simulator, ExchangeProperties properties) {
    this.simulator = simulator;
    this.accountIds = properties.traders().stream().map(TraderProperties::accountId).toList();
  }

  /**
   * Starts a simulator run.
   *
   * @param request the run configuration
   * @return 202 with the initial metrics, 409 if a run is already active, 400 on bad input
   */
  @PostMapping("/admin/simulator/start")
  public ResponseEntity<Object> start(@RequestBody SimulatorStartRequest request) {
    SimulatorConfig config;
    try {
      config =
          new SimulatorConfig(
              request.traderCount(),
              request.ordersPerTrader(),
              request.orderRatePerSecond(),
              request.durationMillis(),
              request.midPrice(),
              request.priceSpreadTicks(),
              request.minQuantity(),
              request.maxQuantity(),
              accountIds,
              request.randomSeed(),
              request.maxLatencySamples());
    } catch (IllegalArgumentException invalid) {
      return ResponseEntity.badRequest().body(Map.of("error", invalid.getMessage()));
    }
    try {
      simulator.start(config);
    } catch (IllegalStateException running) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", running.getMessage()));
    }
    return ResponseEntity.accepted().body(simulator.metrics());
  }

  /**
   * Stops the active run.
   *
   * @return the metrics after requesting a stop
   */
  @PostMapping("/admin/simulator/stop")
  public MetricsSnapshot stop() {
    simulator.stop();
    return simulator.metrics();
  }

  /**
   * The current or most recent run's live metrics.
   *
   * @return the metrics snapshot
   */
  @GetMapping("/admin/simulator/metrics")
  public MetricsSnapshot metrics() {
    return simulator.metrics();
  }
}
