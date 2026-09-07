package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariant 4 — the book is never crossed. After processing any random sequence
 * of valid limit orders, the best bid price is strictly below the best ask.
 */
class BookNeverCrossedProperties {

  @Provide
  Arbitrary<List<Flows.Spec>> flows() {
    return Flows.flows();
  }

  @Property
  void bookIsNeverCrossedAfterAnyStep(@ForAll("flows") List<Flows.Spec> specs) {
    OrderBook book = new OrderBook(new Symbol("BTC", "USD", 1L, 1L));
    long tradeSequence = 0L;
    for (int i = 0; i < specs.size(); i++) {
      List<Trade> trades = Matcher.match(book, Flows.toOrder(specs.get(i), i), tradeSequence);
      tradeSequence += trades.size();
      assertThat(book.isCrossed())
          .as("book crossed after applying order %d", i)
          .isFalse();
      book.bestBid()
          .ifPresent(
              bid ->
                  book.bestAsk()
                      .ifPresent(ask -> assertThat(bid.price()).isLessThan(ask.price())));
    }
  }
}
