package dev.kaloyanyordanov.exchange.sim;

/**
 * A summary of measured latencies, in nanoseconds. All values are computed from
 * real samples via nearest-rank percentiles.
 *
 * @param sampleCount the number of latency samples the summary is computed from
 * @param p50Nanos    the median latency in nanoseconds
 * @param p95Nanos    the 95th-percentile latency in nanoseconds
 * @param p99Nanos    the 99th-percentile latency in nanoseconds
 * @param maxNanos    the maximum observed latency in nanoseconds
 */
public record LatencySummary(
    long sampleCount, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {

  /** An empty summary (no samples). */
  public static final LatencySummary EMPTY = new LatencySummary(0L, 0L, 0L, 0L, 0L);
}
