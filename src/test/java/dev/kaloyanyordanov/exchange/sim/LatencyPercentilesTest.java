package dev.kaloyanyordanov.exchange.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class LatencyPercentilesTest {

  private static long[] oneToHundred() {
    return LongStream.rangeClosed(1, 100).toArray();
  }

  @Test
  void computesKnownNearestRankPercentiles() {
    long[] samples = oneToHundred();
    assertThat(LatencyPercentiles.nearestRank(samples, 50.0)).isEqualTo(50L);
    assertThat(LatencyPercentiles.nearestRank(samples, 95.0)).isEqualTo(95L);
    assertThat(LatencyPercentiles.nearestRank(samples, 99.0)).isEqualTo(99L);
    assertThat(LatencyPercentiles.nearestRank(samples, 100.0)).isEqualTo(100L);
  }

  @Test
  void zeroPercentileClampsToTheFirstSample() {
    assertThat(LatencyPercentiles.nearestRank(oneToHundred(), 0.0)).isEqualTo(1L);
  }

  @Test
  void singleSampleReturnsThatSampleForAnyPercentile() {
    long[] one = {42L};
    assertThat(LatencyPercentiles.nearestRank(one, 50.0)).isEqualTo(42L);
    assertThat(LatencyPercentiles.nearestRank(one, 99.0)).isEqualTo(42L);
  }

  @Test
  void emptySamplesReturnZero() {
    assertThat(LatencyPercentiles.nearestRank(new long[0], 50.0)).isZero();
  }

  @Test
  void percentileOutOfRangeIsRejected() {
    assertThatThrownBy(() -> LatencyPercentiles.nearestRank(oneToHundred(), -1.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LatencyPercentiles.nearestRank(oneToHundred(), 101.0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
