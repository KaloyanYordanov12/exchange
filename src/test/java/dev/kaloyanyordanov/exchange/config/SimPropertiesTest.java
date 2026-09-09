package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimPropertiesTest {

  @Test
  void unsetOrNonPositiveCapMeansUnlimited() {
    assertThat(new SimProperties(null, null, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(0, null, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(-5, null, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void positiveCapIsKept() {
    assertThat(new SimProperties(150, null, null).publicMaxTraders()).isEqualTo(150);
  }

  @Test
  void unsetOrNegativeAmbientMeansOff() {
    assertThat(new SimProperties(null, null, null).ambientTraders()).isZero();
    assertThat(new SimProperties(null, -3, null).ambientTraders()).isZero();
  }

  @Test
  void positiveAmbientIsKept() {
    assertThat(new SimProperties(150, 50, null).ambientTraders()).isEqualTo(50);
  }

  @Test
  void unsetOrNegativeThinkFloorMeansUnrestricted() {
    assertThat(new SimProperties(null, null, null).publicMinThinkTimeMs()).isZero();
    assertThat(new SimProperties(null, null, -100L).publicMinThinkTimeMs()).isZero();
  }

  @Test
  void positiveThinkFloorIsKept() {
    assertThat(new SimProperties(150, 50, 1000L).publicMinThinkTimeMs()).isEqualTo(1000L);
  }
}
