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
 * Matching quantity conservation — units traded balance and no unit is created
 * or destroyed. Under Phase 1 (unconstrained) matching every order either fills
 * or rests its remainder, so for each order {@code filled + resting == original},
 * and the units credited to buyers equal those debited from sellers.
 */
class MatchingQuantityConservationProperties {

  @Provide
  Arbitrary<List<Flows.Spec>> flows() {
    return Flows.flows();
  }

  @Property
  void everyUnitIsEitherFilledOrStillResting(@ForAll("flows") List<Flows.Spec> specs) {
    OrderBook book = new OrderBook(new Symbol("BTC", "USD", 1L, 1L));
    List<Trade> trades = Flows.replay(specs, book);

    Map<Long, Long> filled = new HashMap<>();
    for (Trade trade : trades) {
      filled.merge(trade.buyOrderId().value(), trade.quantity(), Long::sum);
      filled.merge(trade.sellOrderId().value(), trade.quantity(), Long::sum);
    }
    Map<Long, Long> resting = new HashMap<>();
    for (Order order : book.restingOrders()) {
      resting.merge(order.id().value(), order.remaining(), Long::sum);
    }

    for (int i = 0; i < specs.size(); i++) {
      long original = specs.get(i).quantity();
      long accounted = filled.getOrDefault((long) i, 0L) + resting.getOrDefault((long) i, 0L);
      assertThat(accounted)
          .as("order %d: filled + resting must equal original quantity", i)
          .isEqualTo(original);
    }
  }

  @Property
  void unitsBoughtEqualUnitsSold(@ForAll("flows") List<Flows.Spec> specs) {
    OrderBook book = new OrderBook(new Symbol("BTC", "USD", 1L, 1L));
    List<Trade> trades = Flows.replay(specs, book);

    long bought = 0L;
    long sold = 0L;
    for (Trade trade : trades) {
      bought += trade.quantity(); // credited to the buy order
      sold += trade.quantity(); // debited from the sell order
      assertThat(trade.buyOrderId()).isNotEqualTo(trade.sellOrderId());
    }
    assertThat(bought).isEqualTo(sold);
  }
}
