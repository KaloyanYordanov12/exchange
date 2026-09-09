package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimPropertiesTest {

  @Test
  void unsetOrNonPositiveCapMeansUnlimited() {
    assertThat(new SimProperties(null).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(0).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
    assertThat(new SimProperties(-5).publicMaxTraders()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void positiveCapIsKept() {
    assertThat(new SimProperties(150).publicMaxTraders()).isEqualTo(150);
  }
}
