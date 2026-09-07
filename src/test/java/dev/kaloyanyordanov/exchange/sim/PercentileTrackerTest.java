package dev.kaloyanyordanov.exchange.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PercentileTrackerTest {

  @Test
  void rejectsNonPositiveCapacity() {
    assertThatThrownBy(() -> new PercentileTracker(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyTrackerSummarisesToEmpty() {
    assertThat(new PercentileTracker(16).summary()).isEqualTo(LatencySummary.EMPTY);
  }

  @Test
  void computesPercentilesFromSamplesRegardlessOfInsertionOrder() {
    PercentileTracker tracker = new PercentileTracker(1000);
    // Insert 1..100 out of order.
    for (int i = 100; i >= 1; i--) {
      tracker.add(i);
    }
    LatencySummary summary = tracker.summary();
    assertThat(summary.sampleCount()).isEqualTo(100L);
    assertThat(summary.p50Nanos()).isEqualTo(50L);
    assertThat(summary.p95Nanos()).isEqualTo(95L);
    assertThat(summary.p99Nanos()).isEqualTo(99L);
    assertThat(summary.maxNanos()).isEqualTo(100L);
  }

  @Test
  void dropsSamplesBeyondTheCapAndCountsThem() {
    PercentileTracker tracker = new PercentileTracker(3);
    for (int i = 1; i <= 10; i++) {
      tracker.add(i);
    }
    assertThat(tracker.summary().sampleCount()).isEqualTo(3L);
    assertThat(tracker.droppedCount()).isEqualTo(7L);
  }

  @Test
  void growsBackingArrayAsSamplesAreAdded() {
    PercentileTracker tracker = new PercentileTracker(5000);
    for (int i = 1; i <= 3000; i++) {
      tracker.add(1L);
    }
    assertThat(tracker.summary().sampleCount()).isEqualTo(3000L);
    assertThat(tracker.droppedCount()).isZero();
  }
}
