package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.Matcher;
import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderBook;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the deterministic single-threaded result of the exact sequence the
 * engine actually processed (recovered from {@link OrderAccepted} events), so a
 * concurrent run can be checked against it. Determinism is what makes the
 * concurrent outcome assertable at all.
 */
final class EngineReplaySupport {

  private EngineReplaySupport() {}

  /**
   * Replays the accepted orders, in their engine-assigned sequence, through a
   * fresh book and the pure matcher.
   *
   * @param symbol   the traded symbol
   * @param accepted the accepted events (in any order)
   * @param commands the original commands, by order id
   * @return the resulting book
   */
  static OrderBook replay(
      Symbol symbol, List<OrderAccepted> accepted, Map<OrderId, SubmitOrder> commands) {
    List<OrderAccepted> ordered = new ArrayList<>(accepted);
    ordered.sort(Comparator.comparingLong(OrderAccepted::sequence));

    OrderBook book = new OrderBook(symbol);
    long tradeSequence = 0L;
    for (OrderAccepted event : ordered) {
      SubmitOrder command = commands.get(event.id());
      Order order =
          Order.create(
              event.id(),
              command.side(),
              command.price(),
              command.quantity(),
              event.sequence(),
              command.accountId());
      tradeSequence += Matcher.match(book, order, tradeSequence).size();
    }
    return book;
  }

  /**
   * Resting orders sorted by arrival sequence then id, for order-independent
   * comparison between two books.
   *
   * @param orders the resting orders
   * @return a stably sorted copy
   */
  static List<Order> sortedResting(List<Order> orders) {
    List<Order> copy = new ArrayList<>(orders);
    copy.sort(
        Comparator.comparingLong(Order::sequence).thenComparingLong(order -> order.id().value()));
    return copy;
  }
}
