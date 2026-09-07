package dev.kaloyanyordanov.exchange.sim;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real, measured metrics for one simulator run. Producers count submissions and
 * rejections; the metrics sink (on the matching thread) records the processed
 * count, trades, and submit-to-processed latency. Nothing here is fabricated —
 * every number comes from a real event.
 */
final class RunMetrics {

  private final AtomicLong submitted = new AtomicLong();
  private final AtomicLong accepted = new AtomicLong();
  private final AtomicLong rejected = new AtomicLong();
  private final AtomicLong trades = new AtomicLong();
  private final PercentileTracker latency;
  private final ConcurrentHashMap<Long, Long> submitNanos = new ConcurrentHashMap<>();
  private final long startNanos;
  private volatile long endNanos;

  RunMetrics(long startNanos, int maxLatencySamples) {
    this.startNanos = startNanos;
    this.latency = new PercentileTracker(maxLatencySamples);
  }

  void markSubmit(long orderId, long nanos) {
    submitNanos.put(orderId, nanos);
  }

  void unmark(long orderId) {
    submitNanos.remove(orderId);
  }

  void countSubmitted() {
    submitted.incrementAndGet();
  }

  void countRejected() {
    rejected.incrementAndGet();
  }

  /** Called on the matching thread when an order is processed. */
  void onAccepted(long orderId, long processedNanos) {
    Long submittedAt = submitNanos.remove(orderId);
    if (submittedAt != null) {
      latency.add(processedNanos - submittedAt);
      accepted.incrementAndGet();
    }
  }

  /** Called on the matching thread for each trade. */
  void onTrade() {
    trades.incrementAndGet();
  }

  void finish(long nanos) {
    endNanos = nanos;
  }

  boolean isRunning() {
    return endNanos == 0L;
  }

  MetricsSnapshot snapshot(long nowNanos) {
    long end = endNanos != 0L ? endNanos : nowNanos;
    long elapsedNanos = Math.max(1L, end - startNanos);
    long acc = accepted.get();
    double throughput = acc * 1_000_000_000.0 / elapsedNanos;
    return new MetricsSnapshot(
        isRunning(),
        submitted.get(),
        acc,
        rejected.get(),
        trades.get(),
        elapsedNanos / 1_000_000L,
        throughput,
        latency.summary());
  }
}
