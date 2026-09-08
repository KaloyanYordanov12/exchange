package dev.kaloyanyordanov.exchange.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpaForwardControllerTest {

  @Test
  void forwardsClientRoutesToTheAppShell() {
    assertThat(new SpaForwardController().forwardSpaRoutes()).isEqualTo("forward:/index.html");
  }
}
