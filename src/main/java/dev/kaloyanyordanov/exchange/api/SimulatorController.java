package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.sim.SimulatorConfig;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-gated control surface for each pair's load simulator so the UI's simulator
 * panel can start a run, stop it, and read live, real per-pair metrics.
 */
@RestController
public class SimulatorController {

  private final ExchangeRegistry registry;
  private final List<Long> accountIds;

  /**
   * Creates the controller.
   *
   * @param registry   the pair registry
   * @param properties the exchange configuration (for the funded account ids)
   */
  public SimulatorController(ExchangeRegistry registry, ExchangeProperties properties) {
    this.registry = registry;
    this.accountIds = properties.traders().stream().map(TraderProperties::accountId).toList();
  }

  /**
   * Starts a simulator run on the requested pair.
   *
   * @param request the run configuration (including the pair)
   * @return 202 with the initial metrics; 400 bad input; 404 unknown pair; 409 if a
   *     run is already active on that pair
   */
  @PostMapping("/admin/simulator/start")
  public ResponseEntity<Object> start(@RequestBody SimulatorStartRequest request) {
    if (request.pair() == null || !registry.hasPair(request.pair())) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
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
      registry.startSimulator(request.pair(), config);
    } catch (IllegalStateException running) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", running.getMessage()));
    }
    return ResponseEntity.accepted().body(registry.simulatorMetrics(request.pair()));
  }

  /**
   * Stops the active run on a pair.
   *
   * @param pair the pair id
   * @return the metrics after requesting a stop, or 404 for an unknown pair
   */
  @PostMapping("/admin/simulator/stop")
  public ResponseEntity<Object> stop(@RequestParam String pair) {
    if (!registry.hasPair(pair)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
    registry.stopSimulator(pair);
    return ResponseEntity.ok(registry.simulatorMetrics(pair));
  }

  /**
   * The current or most recent run's live metrics for a pair.
   *
   * @param pair the pair id
   * @return the metrics snapshot, or 404 for an unknown pair
   */
  @GetMapping("/admin/simulator/metrics")
  public ResponseEntity<Object> metrics(@RequestParam String pair) {
    if (!registry.hasPair(pair)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
    return ResponseEntity.ok(registry.simulatorMetrics(pair));
  }
}
