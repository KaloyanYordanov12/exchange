package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderTest {

  private static Order sample() {
    return Order.create(OrderId.of(1L), Side.BUY, 100L, 10L, 0L, 7L);
  }

  @Test
  void createStartsFullyUnfilled() {
    Order order = sample();
    assertThat(order.remaining()).isEqualTo(10L);
    assertThat(order.quantity()).isEqualTo(10L);
    assertThat(order.filled()).isZero();
    assertThat(order.isFullyFilled()).isFalse();
    assertThat(order.accountId()).isEqualTo(7L);
    assertThat(order.sequence()).isZero();
    assertThat(order.side()).isEqualTo(Side.BUY);
    assertThat(order.price()).isEqualTo(100L);
  }

  @Test
  void withRemainingProducesNewInstanceAndUpdatesFilled() {
    Order order = sample();
    Order partly = order.withRemaining(4L);
    assertThat(partly).isNotSameAs(order);
    assertThat(partly.remaining()).isEqualTo(4L);
    assertThat(partly.filled()).isEqualTo(6L);
    assertThat(partly.isFullyFilled()).isFalse();
    assertThat(order.remaining()).isEqualTo(10L);
  }

  @Test
  void fullyFilledWhenRemainingZero() {
    assertThat(sample().withRemaining(0L).isFullyFilled()).isTrue();
  }

  @Test
  void remainingEqualToQuantityIsAllowed() {
    assertThat(sample().withRemaining(10L).remaining()).isEqualTo(10L);
  }

  @Test
  void nonPositivePriceRejected() {
    assertThatThrownBy(() -> Order.create(OrderId.of(1L), Side.BUY, 0L, 10L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPositiveQuantityRejected() {
    assertThatThrownBy(() -> Order.create(OrderId.of(1L), Side.BUY, 100L, 0L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void remainingAboveQuantityRejected() {
    assertThatThrownBy(() -> new Order(OrderId.of(1L), Side.BUY, 100L, 10L, 11L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeRemainingRejected() {
    assertThatThrownBy(() -> new Order(OrderId.of(1L), Side.BUY, 100L, 10L, -1L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeSequenceRejected() {
    assertThatThrownBy(() -> new Order(OrderId.of(1L), Side.BUY, 100L, 10L, 10L, -1L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullIdRejected() {
    assertThatThrownBy(() -> new Order(null, Side.BUY, 100L, 10L, 10L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullSideRejected() {
    assertThatThrownBy(() -> new Order(OrderId.of(1L), null, 100L, 10L, 10L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
