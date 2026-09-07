package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.invariant.InvariantMonitor;
import dev.kaloyanyordanov.exchange.invariant.InvariantReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only invariant panel. Anyone can watch the seven invariants stay
 * green under load without any admin control. {@code /invariants} returns the
 * latest continuous verdict cheaply; {@code /invariants/check} forces a fresh
 * check.
 */
@RestController
public class InvariantController {

  private final InvariantMonitor monitor;

  /**
   * Creates the controller.
   *
   * @param monitor the invariant monitor
   */
  public InvariantController(InvariantMonitor monitor) {
    this.monitor = monitor;
  }

  /**
   * The latest continuous invariant verdict.
   *
   * @return the latest report
   */
  @GetMapping("/invariants")
  public InvariantReport latest() {
    return monitor.latest();
  }

  /**
   * Forces a fresh on-demand invariant check.
   *
   * @return the fresh report
   */
  @GetMapping("/invariants/check")
  public InvariantReport check() {
    return monitor.checkNow();
  }
}
