package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.invariant.InvariantReport;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only per-pair invariant panel. Anyone can watch a pair's invariants
 * stay green under load without any admin control. {@code /invariants?pair=} returns
 * the latest continuous verdict cheaply; {@code /invariants/check?pair=} forces a
 * fresh check.
 */
@RestController
public class InvariantController {

  private final ExchangeRegistry registry;

  /**
   * Creates the controller.
   *
   * @param registry the pair registry
   */
  public InvariantController(ExchangeRegistry registry) {
    this.registry = registry;
  }

  /**
   * The latest continuous invariant verdict for a pair.
   *
   * @param pair the pair id
   * @return the latest report, or 404 for an unknown pair
   */
  @GetMapping("/invariants")
  public ResponseEntity<Object> latest(@RequestParam String pair) {
    if (!registry.hasPair(pair)) {
      return unknownPair();
    }
    return ResponseEntity.ok(registry.invariants(pair));
  }

  /**
   * Forces a fresh on-demand invariant check for a pair.
   *
   * @param pair the pair id
   * @return the fresh report, or 404 for an unknown pair
   */
  @GetMapping("/invariants/check")
  public ResponseEntity<Object> check(@RequestParam String pair) {
    if (!registry.hasPair(pair)) {
      return unknownPair();
    }
    InvariantReport report = registry.checkInvariants(pair);
    return ResponseEntity.ok(report);
  }

  private static ResponseEntity<Object> unknownPair() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
  }
}
