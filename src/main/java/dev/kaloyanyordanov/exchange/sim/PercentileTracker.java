package dev.kaloyanyordanov.exchange.sim;

import java.util.Arrays;

/**
 * Collects latency samples and computes nearest-rank percentiles from them. Adds
 * are cheap and happen on the matching thread (from the metrics sink); reads copy
 * the samples under a short lock and compute outside it. Bounded: once the sample
 * cap is reached, further samples are counted as dropped rather than stored, so
 * memory stays bounded — the summary honestly reports how many samples it used.
 */
public final class PercentileTracker {

  private final int maxSamples;
  private long[] samples;
  private int size;
  private long dropped;

  /**
   * Creates a tracker.
   *
   * @param maxSamples the maximum samples retained for percentile computation
   */
  public PercentileTracker(int maxSamples) {
    if (maxSamples <= 0) {
      throw new IllegalArgumentException("maxSamples must be positive: " + maxSamples);
    }
    this.maxSamples = maxSamples;
    this.samples = new long[Math.min(maxSamples, 1024)];
  }

  /**
   * Records one latency sample.
   *
   * @param sampleNanos the latency in nanoseconds
   */
  public synchronized void add(long sampleNanos) {
    if (size >= maxSamples) {
      dropped++;
      return;
    }
    if (size == samples.length) {
      int grown = Math.min(maxSamples, samples.length * 2);
      samples = Arrays.copyOf(samples, grown);
    }
    samples[size++] = sampleNanos;
  }

  /**
   * A summary of the recorded latencies via nearest-rank percentiles.
   *
   * @return the latency summary (empty if no samples)
   */
  public LatencySummary summary() {
    long[] copy;
    int count;
    synchronized (this) {
      count = size;
      copy = Arrays.copyOf(samples, count);
    }
    if (count == 0) {
      return LatencySummary.EMPTY;
    }
    Arrays.sort(copy);
    return new LatencySummary(
        count,
        LatencyPercentiles.nearestRank(copy, 50.0),
        LatencyPercentiles.nearestRank(copy, 95.0),
        LatencyPercentiles.nearestRank(copy, 99.0),
        copy[count - 1]);
  }

  /**
   * The number of samples dropped because the cap was reached.
   *
   * @return the dropped count
   */
  public synchronized long droppedCount() {
    return dropped;
  }
}
