package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimPropertiesTest {

  @Test
  void unsetOrNonPositiveCapMeansUnlimited() {
    assertThat(new SimProperties(null, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(0, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(-5, null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void positiveCapIsKept() {
    assertThat(new SimProperties(150, null).publicMaxTraders()).isEqualTo(150);
  }

  @Test
  void unsetOrNegativeAmbientMeansOff() {
    assertThat(new SimProperties(null, null).ambientTraders()).isZero();
    assertThat(new SimProperties(null, -3).ambientTraders()).isZero();
  }

  @Test
  void positiveAmbientIsKept() {
    assertThat(new SimProperties(150, 50).ambientTraders()).isEqualTo(50);
  }
}
