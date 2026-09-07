package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TradeTest {

  private static Trade sample() {
    return new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 10L, 20L, 3L);
  }

  @Test
  void exposesFields() {
    Trade trade = sample();
    assertThat(trade.buyOrderId()).isEqualTo(OrderId.of(1L));
    assertThat(trade.sellOrderId()).isEqualTo(OrderId.of(2L));
    assertThat(trade.price()).isEqualTo(100L);
    assertThat(trade.quantity()).isEqualTo(5L);
    assertThat(trade.buyerAccountId()).isEqualTo(10L);
    assertThat(trade.sellerAccountId()).isEqualTo(20L);
    assertThat(trade.sequence()).isEqualTo(3L);
  }

  @Test
  void notionalIsPriceTimesQuantity() {
    assertThat(sample().notional()).isEqualTo(500L);
  }

  @Test
  void notionalOverflowThrows() {
    Trade huge = new Trade(OrderId.of(1L), OrderId.of(2L), Long.MAX_VALUE, 2L, 10L, 20L, 3L);
    assertThatThrownBy(huge::notional).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void nullBuyIdRejected() {
    assertThatThrownBy(() -> new Trade(null, OrderId.of(2L), 100L, 5L, 10L, 20L, 3L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullSellIdRejected() {
    assertThatThrownBy(() -> new Trade(OrderId.of(1L), null, 100L, 5L, 10L, 20L, 3L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPositivePriceRejected() {
    assertThatThrownBy(() -> new Trade(OrderId.of(1L), OrderId.of(2L), 0L, 5L, 10L, 20L, 3L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPositiveQuantityRejected() {
    assertThatThrownBy(() -> new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 0L, 10L, 20L, 3L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeSequenceRejected() {
    assertThatThrownBy(() -> new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 10L, 20L, -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
