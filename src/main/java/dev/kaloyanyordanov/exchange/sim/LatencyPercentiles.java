package dev.kaloyanyordanov.exchange.sim;

/**
 * Nearest-rank percentile computation over a sorted sample array. Deterministic
 * and pure, so latency percentiles are exactly reproducible and unit-testable —
 * the reported numbers are computed, never fabricated.
 */
public final class LatencyPercentiles {

  private LatencyPercentiles() {}

  /**
   * The nearest-rank percentile of an ascending-sorted sample array. For a
   * percentile {@code p} over {@code n} samples the rank is {@code ceil(p/100 * n)},
   * clamped to {@code [1, n]}, and the sample at that 1-based rank is returned.
   *
   * @param sortedAscending samples sorted ascending (not modified)
   * @param percentile      the percentile in {@code [0, 100]}
   * @return the sample at the nearest rank, or {@code 0} if there are no samples
   */
  public static long nearestRank(long[] sortedAscending, double percentile) {
    if (percentile < 0.0 || percentile > 100.0) {
      throw new IllegalArgumentException("percentile must be in [0, 100]: " + percentile);
    }
    int n = sortedAscending.length;
    if (n == 0) {
      return 0L;
    }
    int rank = (int) Math.ceil(percentile / 100.0 * n);
    if (rank < 1) {
      rank = 1;
    }
    if (rank > n) {
      rank = n;
    }
    return sortedAscending[rank - 1];
  }
}
