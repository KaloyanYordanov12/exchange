package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.config.SimProperties;
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
  private final int publicMaxTraders;
  private final long publicMinThinkMillis;

  /**
   * Creates the controller.
   *
   * @param registry      the pair registry
   * @param properties    the exchange configuration (for the funded account ids)
   * @param simProperties the simulator deployment controls (public trader cap and
   *     think-time floor)
   */
  public SimulatorController(
      ExchangeRegistry registry, ExchangeProperties properties, SimProperties simProperties) {
    this.registry = registry;
    this.accountIds = properties.traders().stream().map(TraderProperties::accountId).toList();
    this.publicMaxTraders = simProperties.publicMaxTraders();
    this.publicMinThinkMillis = simProperties.publicMinThinkTimeMs();
  }

  /**
   * The server-enforced simulator limits (public, read-only). The UI reads these to
   * bound its controls to exactly what the server will run: the trader-count cap and
   * the per-trader think-time floor. Server-side clamping in {@link #start} is the real
   * protection; this endpoint only lets the UI reflect it.
   *
   * @return the public trader cap and the minimum per-trader think-time in millis
   */
  @GetMapping("/simulator/limits")
  public Map<String, Number> limits() {
    return Map.of(
        "maxTraders", publicMaxTraders,
        "minThinkMillis", publicMinThinkMillis);
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
    // Hard cap: a request above the public limit is clamped down to it, so no caller
    // can exhaust a constrained deployment. Locally the cap is unlimited.
    int traderCount = Math.min(request.traderCount(), publicMaxTraders);
    // Think-time floor: a request below the floor (including 0 = max speed) has both
    // think-time bounds raised to it, so a visitor cannot run traders at zero think-time.
    // Raising both bounds preserves min <= max and forces think-time pacing on (a nonzero
    // max means orderRatePerSecond is not used). Locally the floor is 0 (unrestricted).
    long minThinkMillis = Math.max(request.minThinkMillis(), publicMinThinkMillis);
    long maxThinkMillis = Math.max(request.maxThinkMillis(), publicMinThinkMillis);
    SimulatorConfig config;
    try {
      config =
          new SimulatorConfig(
              traderCount,
              request.ordersPerTrader(),
              request.orderRatePerSecond(),
              request.durationMillis(),
              request.midPrice(),
              request.priceSpreadTicks(),
              request.minQuantity(),
              request.maxQuantity(),
              accountIds,
              request.randomSeed(),
              request.maxLatencySamples(),
              minThinkMillis,
              maxThinkMillis,
              request.aggression());
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
