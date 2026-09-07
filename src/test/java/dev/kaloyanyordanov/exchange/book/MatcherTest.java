package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatcherTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private static OrderBook book() {
    return new OrderBook(SYMBOL);
  }

  private static Order order(long id, Side side, long price, long qty, long seq) {
    return Order.create(OrderId.of(id), side, price, qty, seq, id);
  }

  @Test
  void nonCrossingBuyRestsAsBid() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 99L, 5L, 2), 0L);
    assertThat(trades).isEmpty();
    assertThat(book.bestBid()).map(Order::id).contains(OrderId.of(2L));
    assertThat(book.isCrossed()).isFalse();
  }

  @Test
  void emptyBookRestsIncoming() {
    OrderBook book = book();
    List<Trade> trades = Matcher.match(book, order(1, Side.BUY, 100L, 5L, 1), 0L);
    assertThat(trades).isEmpty();
    assertThat(book.bestBid()).map(Order::remaining).contains(5L);
  }

  @Test
  void exactFillRemovesMakerAndRestsNothing() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 100L, 5L, 2), 0L);
    assertThat(trades).hasSize(1);
    Trade t = trades.get(0);
    assertThat(t.price()).isEqualTo(100L);
    assertThat(t.quantity()).isEqualTo(5L);
    assertThat(t.buyOrderId()).isEqualTo(OrderId.of(2L));
    assertThat(t.sellOrderId()).isEqualTo(OrderId.of(1L));
    assertThat(book.isEmpty()).isTrue();
  }

  @Test
  void takerLargerThanMakerFillsMakerAndRestsRemainder() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 100L, 8L, 2), 0L);
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).quantity()).isEqualTo(5L);
    // Remainder of 3 rests as a bid; no asks remain so it is not crossing.
    assertThat(book.bestAsk()).isEmpty();
    assertThat(book.bestBid()).get().extracting(Order::id, Order::remaining)
        .containsExactly(OrderId.of(2L), 3L);
    assertThat(book.isCrossed()).isFalse();
  }

  @Test
  void takerSmallerThanMakerPartiallyFillsMakerInPlace() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 100L, 2L, 2), 0L);
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).quantity()).isEqualTo(2L);
    assertThat(book.bestAsk()).get().extracting(Order::id, Order::remaining)
        .containsExactly(OrderId.of(1L), 3L);
    assertThat(book.bestBid()).isEmpty();
  }

  @Test
  void executesAtMakerPriceNotTakerPrice() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 105L, 5L, 2), 0L);
    assertThat(trades.get(0).price()).isEqualTo(100L);
  }

  @Test
  void crossesCheapestAskFirstThenNext() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 102L, 3L, 1));
    book.addResting(order(2, Side.SELL, 100L, 4L, 2));
    List<Trade> trades = Matcher.match(book, order(3, Side.BUY, 105L, 6L, 3), 10L);
    assertThat(trades).extracting(Trade::price).containsExactly(100L, 102L);
    assertThat(trades).extracting(Trade::quantity).containsExactly(4L, 2L);
    assertThat(trades).extracting(Trade::sequence).containsExactly(10L, 11L);
    // 102-level maker (id 1) had 3, filled 2, leaves 1 resting.
    assertThat(book.bestAsk()).get().extracting(Order::id, Order::remaining)
        .containsExactly(OrderId.of(1L), 1L);
  }

  @Test
  void fifoWithinPriceLevelEarliestFirst() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 3L, 1));
    book.addResting(order(2, Side.SELL, 100L, 3L, 2));
    List<Trade> trades = Matcher.match(book, order(3, Side.BUY, 100L, 4L, 3), 0L);
    assertThat(trades).extracting(Trade::sellOrderId)
        .containsExactly(OrderId.of(1L), OrderId.of(2L));
    // Second maker (id 2) had 3, filled 1, leaves 2.
    assertThat(book.bestAsk()).get().extracting(Order::id, Order::remaining)
        .containsExactly(OrderId.of(2L), 2L);
  }

  @Test
  void incomingSellCrossesBestBid() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    book.addResting(order(2, Side.BUY, 101L, 5L, 2));
    List<Trade> trades = Matcher.match(book, order(3, Side.SELL, 100L, 5L, 3), 0L);
    assertThat(trades).hasSize(1);
    Trade t = trades.get(0);
    // Best bid is 101 (highest); sell executes there.
    assertThat(t.price()).isEqualTo(101L);
    assertThat(t.buyOrderId()).isEqualTo(OrderId.of(2L));
    assertThat(t.sellOrderId()).isEqualTo(OrderId.of(3L));
    assertThat(book.bestBid()).map(Order::price).contains(100L);
  }

  @Test
  void negativeSequenceRejected() {
    assertThatThrownBy(() -> Matcher.match(book(), order(1, Side.BUY, 100L, 5L, 1), -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullArgumentsRejected() {
    OrderBook book = book();
    Order o = order(1, Side.BUY, 100L, 5L, 1);
    assertThatThrownBy(() -> Matcher.match(null, o, 0L))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Matcher.match(book, null, 0L))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Matcher.match(book, o, null, 0L))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- affordability branches via a stub policy ----

  private static final class StubPolicy implements FillPolicy {
    private final long buyerCap;
    private final long sellerCap;
    private final List<Trade> settled = new ArrayList<>();

    StubPolicy(long buyerCap, long sellerCap) {
      this.buyerCap = buyerCap;
      this.sellerCap = sellerCap;
    }

    @Override
    public long maxBuyerUnits(long buyerAccountId, long tradePrice) {
      return buyerCap;
    }

    @Override
    public long maxSellerUnits(long sellerAccountId) {
      return sellerCap;
    }

    @Override
    public void onFill(Trade trade) {
      settled.add(trade);
    }
  }

  @Test
  void onFillCalledForEachTrade() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 3L, 1));
    book.addResting(order(2, Side.SELL, 101L, 3L, 2));
    StubPolicy policy = new StubPolicy(Long.MAX_VALUE, Long.MAX_VALUE);
    List<Trade> trades = Matcher.match(book, order(3, Side.BUY, 105L, 6L, 3), policy, 0L);
    assertThat(policy.settled).isEqualTo(trades).hasSize(2);
  }

  @Test
  void deadMakerDroppedWhenSellerCannotDeliver() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    book.addResting(order(2, Side.SELL, 101L, 5L, 2));
    // Seller can deliver nothing: every maker is dropped, taker rests, no trades.
    StubPolicy policy = new StubPolicy(Long.MAX_VALUE, 0L);
    List<Trade> trades = Matcher.match(book, order(3, Side.BUY, 105L, 5L, 3), policy, 0L);
    assertThat(trades).isEmpty();
    assertThat(book.bestAsk()).isEmpty();
    assertThat(book.bestBid()).map(Order::id).contains(OrderId.of(3L));
  }

  @Test
  void blockedBuyerRejectsRemainderRatherThanCrossing() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 5L, 1));
    // Buyer can afford nothing: remainder is rejected, NOT rested (would cross).
    StubPolicy policy = new StubPolicy(0L, Long.MAX_VALUE);
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 100L, 5L, 2), policy, 0L);
    assertThat(trades).isEmpty();
    assertThat(book.bestBid()).isEmpty();
    assertThat(book.bestAsk()).map(Order::id).contains(OrderId.of(1L));
    assertThat(book.isCrossed()).isFalse();
  }

  @Test
  void blockedSellerRejectsRemainder() {
    OrderBook book = book();
    book.addResting(order(1, Side.BUY, 100L, 5L, 1));
    // Incoming seller can deliver nothing: remainder rejected, not rested.
    StubPolicy policy = new StubPolicy(Long.MAX_VALUE, 0L);
    List<Trade> trades = Matcher.match(book, order(2, Side.SELL, 100L, 5L, 2), policy, 0L);
    assertThat(trades).isEmpty();
    assertThat(book.bestAsk()).isEmpty();
    assertThat(book.bestBid()).map(Order::id).contains(OrderId.of(1L));
  }

  @Test
  void affordabilityCapLimitsFillQuantity() {
    OrderBook book = book();
    book.addResting(order(1, Side.SELL, 100L, 10L, 1));
    // Buyer affords only 4 units at a time; seller unlimited.
    StubPolicy policy = new StubPolicy(4L, Long.MAX_VALUE);
    List<Trade> trades = Matcher.match(book, order(2, Side.BUY, 100L, 3L, 2), policy, 0L);
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).quantity()).isEqualTo(3L);
  }
}
