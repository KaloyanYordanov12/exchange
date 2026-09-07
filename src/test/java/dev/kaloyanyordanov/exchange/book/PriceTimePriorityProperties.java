package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariant 5 — price-time priority. Every fill consumes the best-priced, then
 * earliest, resting order: at each matching step the first resulting trade's
 * maker is exactly the head of the best opposite level captured beforehand
 * (best price, and earliest at that price since the head is the oldest).
 */
class PriceTimePriorityProperties {

  @Provide
  Arbitrary<List<Flows.Spec>> flows() {
    return Flows.flows();
  }

  @Property
  void firstFillAlwaysHitsBestThenEarliestResting(@ForAll("flows") List<Flows.Spec> specs) {
    OrderBook book = new OrderBook(new Symbol("BTC", "USD", 1L, 1L));
    long tradeSequence = 0L;
    for (int i = 0; i < specs.size(); i++) {
      Order incoming = Flows.toOrder(specs.get(i), i);
      Optional<Order> bestOpposite = book.bestOrder(incoming.side().opposite());

      List<Trade> trades = Matcher.match(book, incoming, tradeSequence);
      tradeSequence += trades.size();

      if (!trades.isEmpty()) {
        // A trade happened, so there must have been an opposite order that crossed.
        assertThat(bestOpposite).isPresent();
        Order maker = bestOpposite.orElseThrow();
        Trade first = trades.get(0);
        OrderId makerId =
            incoming.side() == Side.BUY ? first.sellOrderId() : first.buyOrderId();
        assertThat(makerId)
            .as("first fill must hit the best, earliest resting order")
            .isEqualTo(maker.id());
        assertThat(first.price())
            .as("first fill executes at the best opposite (maker) price")
            .isEqualTo(maker.price());
      }
    }
  }
}
