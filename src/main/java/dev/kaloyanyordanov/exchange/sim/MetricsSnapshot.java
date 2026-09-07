package dev.kaloyanyordanov.exchange.sim;

/**
 * A point-in-time view of a simulator run's real, measured metrics.
 *
 * @param running              whether the run is still in progress
 * @param submitted            orders accepted onto the ingress queue
 * @param accepted             orders processed by the matching thread
 * @param rejected             orders rejected by back-pressure (queue full)
 * @param trades               trades executed during the run
 * @param elapsedMillis        elapsed run time in milliseconds
 * @param throughputPerSecond  processed orders per second (accepted / elapsed)
 * @param latency              submit-to-processed latency summary (nanoseconds)
 */
public record MetricsSnapshot(
    boolean running,
    long submitted,
    long accepted,
    long rejected,
    long trades,
    long elapsedMillis,
    double throughputPerSecond,
    LatencySummary latency) {

  /** An empty snapshot (no run has been started). */
  public static final MetricsSnapshot EMPTY =
      new MetricsSnapshot(false, 0L, 0L, 0L, 0L, 0L, 0.0, LatencySummary.EMPTY);
}
