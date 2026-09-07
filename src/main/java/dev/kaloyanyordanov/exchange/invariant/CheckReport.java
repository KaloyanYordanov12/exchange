package dev.kaloyanyordanov.exchange.invariant;

import java.util.List;

/**
 * The outcome of checking all invariants against a snapshot: an overall pass flag
 * plus a per-invariant result.
 *
 * @param allPassed whether every invariant held
 * @param results   the per-invariant results
 */
public record CheckReport(boolean allPassed, List<InvariantResult> results) {

  /** Defensive copy. */
  public CheckReport {
    results = List.copyOf(results);
  }

  /**
   * Builds a report from the results, computing the overall pass flag.
   *
   * @param results the per-invariant results
   * @return the report
   */
  public static CheckReport of(List<InvariantResult> results) {
    boolean all = results.stream().allMatch(InvariantResult::passed);
    return new CheckReport(all, results);
  }
}
