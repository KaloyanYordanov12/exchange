package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderIdTest {

  @Test
  void keepsItsValue() {
    assertThat(OrderId.of(42L).value()).isEqualTo(42L);
  }

  @Test
  void zeroIsAllowed() {
    assertThat(OrderId.of(0L).value()).isZero();
  }

  @Test
  void negativeIsRejected() {
    assertThatThrownBy(() -> OrderId.of(-1L)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalsByValue() {
    assertThat(OrderId.of(7L)).isEqualTo(OrderId.of(7L)).isNotEqualTo(OrderId.of(8L));
  }
}
