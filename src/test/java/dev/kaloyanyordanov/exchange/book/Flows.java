package dev.kaloyanyordanov.exchange.book;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Shared jqwik generators and a deterministic replay helper for property tests
 * over random order sequences. Values are bounded (small prices/quantities) so
 * scaled-integer arithmetic never overflows, keeping the properties meaningful.
 */
final class Flows {

  private Flows() {}

  /** A generated order intent, before an id and arrival sequence are assigned. */
  record Spec(Side side, long price, long quantity, long account) {}

  /**
   * Random order flows: 0..60 specs with prices in a tight band (so orders
   * frequently cross), small quantities, and a handful of accounts.
   *
   * @return an arbitrary of order-spec lists
   */
  static Arbitrary<List<Spec>> flows() {
    Arbitrary<Side> sides = Arbitraries.of(Side.BUY, Side.SELL);
    Arbitrary<Long> prices = Arbitraries.longs().between(1L, 20L);
    Arbitrary<Long> quantities = Arbitraries.longs().between(1L, 10L);
    Arbitrary<Long> accounts = Arbitraries.longs().between(0L, 5L);
    Arbitrary<Spec> spec = Combinators.combine(sides, prices, quantities, accounts).as(Spec::new);
    return spec.list().ofMinSize(0).ofMaxSize(60);
  }

  /**
   * Turns a spec into an order, using the list index as both the unique id and
   * the arrival sequence (so time priority mirrors submission order).
   *
   * @param spec  the intent
   * @param index the submission index
   * @return the order
   */
  static Order toOrder(Spec spec, long index) {
    return Order.create(
        OrderId.of(index), spec.side(), spec.price(), spec.quantity(), index, spec.account());
  }

  /**
   * Replays a flow through the pure matcher against a book, returning every
   * trade produced, in order. Trade sequence numbers run contiguously.
   *
   * @param specs the order flow
   * @param book  the book to apply the flow to (mutated)
   * @return all trades produced across the flow
   */
  static List<Trade> replay(List<Spec> specs, OrderBook book) {
    List<Trade> all = new ArrayList<>();
    long tradeSequence = 0L;
    for (int i = 0; i < specs.size(); i++) {
      List<Trade> trades = Matcher.match(book, toOrder(specs.get(i), i), tradeSequence);
      all.addAll(trades);
      tradeSequence += trades.size();
    }
    return all;
  }
}
