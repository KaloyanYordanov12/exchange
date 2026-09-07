package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariant 6 — no order is overfilled. Across any random sequence, the
 * cumulative filled quantity of every order never exceeds its original quantity.
 */
class NoOverfillProperties {

  @Provide
  Arbitrary<List<Flows.Spec>> flows() {
    return Flows.flows();
  }

  @Property
  void cumulativeFillNeverExceedsOriginalQuantity(@ForAll("flows") List<Flows.Spec> specs) {
    OrderBook book = new OrderBook(new Symbol("BTC", "USD", 1L, 1L));
    List<Trade> trades = Flows.replay(specs, book);

    Map<OrderId, Long> filled = new HashMap<>();
    for (Trade trade : trades) {
      filled.merge(trade.buyOrderId(), trade.quantity(), Long::sum);
      filled.merge(trade.sellOrderId(), trade.quantity(), Long::sum);
    }

    for (Map.Entry<OrderId, Long> entry : filled.entrySet()) {
      long originalQuantity = specs.get((int) entry.getKey().value()).quantity();
      assertThat(entry.getValue())
          .as("order %d overfilled", entry.getKey().value())
          .isLessThanOrEqualTo(originalQuantity);
    }
  }
}
