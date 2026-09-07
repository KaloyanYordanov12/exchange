package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderBookTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private OrderBook book() {
    return new OrderBook(SYMBOL);
  }

  private static Order order(long id, Side side, long price, long qty, long seq) {
    return Order.create(OrderId.of(id), side, price, qty, seq, id);
  }

  @Test
  void nullSymbolRejected() {
    assertThatThrownBy(() -> new OrderBook(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void newBookIsEmpty() {
    OrderBook book = book();
    assertThat(book.isEmpty()).isTrue();
    assertThat(book.bestBid()).isEmpty();
    assertThat(book.bestAsk()).isEmpty();
    assertThat(book.isCrossed()).isFalse();
    assertThat(book.symbol()).isEqualTo(SYMBOL);
  }

  @Test
  void bestBidIsHighestPrice() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    book.addResting(order(2, Side.BUY, 105L, 5L, 2));
    book.addResting(order(3, Side.BUY, 95L, 5L, 3));
    assertThat(book.bestBid()).map(Order::price).contains(105L);
    assertThat(book.isEmpty()).isFalse();
  }

  @Test
  void bestAskIsLowestPrice() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 110L, 5L, 1));
    book.addResting(order(2, Side.SELL, 108L, 5L, 2));
    book.addResting(order(3, Side.SELL, 120L, 5L, 3));
    assertThat(book.bestAsk()).map(Order::price).contains(108L);
  }

  @Test
  void fifoWithinPriceLevel() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    book.addResting(order(2, Side.BUY, 100L, 7L, 2));
    // Earliest (seq 1) rests at the head.
    assertThat(book.bestBid()).map(Order::id).contains(OrderId.of(1L));
  }

  @Test
  void addRestingRejectsNull() {
    assertThatThrownBy(() -> book().addResting(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addRestingRejectsZeroRemaining() {
    Order filled = order(1, Side.BUY, 100L, 5L, 1).withRemaining(0L);
    assertThatThrownBy(() -> book().addResting(filled))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void crossedWhenBidAtOrAboveAsk() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    book.addResting(order(2, Side.SELL, 100L, 5L, 2));
    assertThat(book.isCrossed()).isTrue();
  }

  @Test
  void notCrossedWhenBidBelowAsk() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 99L, 5L, 1));
    book.addResting(order(2, Side.SELL, 100L, 5L, 2));
    assertThat(book.isCrossed()).isFalse();
  }

  @Test
  void removeBestDropsEmptyLevel() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    book.removeBest(Side.SELL);
    assertThat(book.bestAsk()).isEmpty();
    assertThat(book.isEmpty()).isTrue();
  }

  @Test
  void removeBestKeepsNonEmptyLevel() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    book.addResting(order(2, Side.SELL, 100L, 6L, 2));
    book.removeBest(Side.SELL);
    assertThat(book.bestAsk()).map(Order::id).contains(OrderId.of(2L));
  }

  @Test
  void removeBestAdvancesToNextPriceLevel() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    book.addResting(order(2, Side.SELL, 101L, 6L, 2));
    book.removeBest(Side.SELL);
    assertThat(book.bestAsk()).map(Order::price).contains(101L);
  }

  @Test
  void replaceBestReducesHeadInPlace() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    book.addResting(order(2, Side.SELL, 100L, 6L, 2));
    Order head = book.bestAsk().orElseThrow();
    book.replaceBest(Side.SELL, head.withRemaining(2L));
    assertThat(book.bestAsk()).get().extracting(Order::id, Order::remaining)
        .containsExactly(OrderId.of(1L), 2L);
  }

  @Test
  void snapshotAggregatesRemainingPerLevelBestFirst() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    book.addResting(order(2, Side.BUY, 100L, 3L, 2));
    book.addResting(order(3, Side.BUY, 99L, 4L, 3));
    book.addResting(order(4, Side.SELL, 110L, 6L, 4));

    BookSnapshot snapshot = book.snapshot();
    assertThat(snapshot.bids())
        .containsExactly(new PriceLevel(100L, 8L), new PriceLevel(99L, 4L));
    assertThat(snapshot.asks()).containsExactly(new PriceLevel(110L, 6L));
  }

  @Test
  void snapshotIsImmutable() {
    BookSnapshot snapshot = book().snapshot();
    assertThatThrownBy(() -> snapshot.bids().add(new PriceLevel(1L, 1L)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
