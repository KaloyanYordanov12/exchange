package dev.kaloyanyordanov.exchange.invariant;

import java.util.List;

/**
 * The result exposed to the live panel: whether a consistent snapshot was
 * available and, if so, the per-invariant verdict. When a snapshot could not be
 * obtained (e.g. the engine is momentarily saturated) it is reported as
 * unavailable rather than silently green.
 *
 * @param available whether a snapshot was obtained and checked
 * @param allPassed whether every invariant held (false when unavailable)
 * @param results   the per-invariant results (empty when unavailable)
 */
public record InvariantReport(boolean available, boolean allPassed, List<InvariantResult> results) {

  /** Defensive copy. */
  public InvariantReport {
    results = List.copyOf(results);
  }

  /**
   * A report from a completed check.
   *
   * @param report the check report
   * @return an available invariant report
   */
  public static InvariantReport of(CheckReport report) {
    return new InvariantReport(true, report.allPassed(), report.results());
  }

  /**
   * A report indicating no snapshot could be obtained.
   *
   * @return an unavailable report
   */
  public static InvariantReport unavailable() {
    return new InvariantReport(false, false, List.of());
  }
}
