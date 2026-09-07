package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.Matcher;
import dev.kaloyanyordanov.exchange.book.OrderBook;
import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Invariant 2 — asset conservation. Over any random sequence of funded orders,
 * total asset units across all accounts stay constant after every step.
 */
class AssetConservationProperties {

  @Provide
  Arbitrary<LedgerFlows.Scenario> scenarios() {
    return LedgerFlows.scenarios();
  }

  @Property
  void totalAssetIsInvariant(@ForAll("scenarios") LedgerFlows.Scenario scenario) {
    Ledger ledger = LedgerFlows.fund(scenario);
    long initialAsset = ledger.totalAsset();

    OrderBook book = new OrderBook(LedgerFlows.SYMBOL);
    long tradeSequence = 0L;
    List<LedgerFlows.Spec> orders = scenario.orders();
    for (int i = 0; i < orders.size(); i++) {
      tradeSequence +=
          Matcher.match(book, LedgerFlows.toOrder(orders.get(i), i), ledger, tradeSequence).size();
      assertThat(ledger.totalAsset())
          .as("total asset changed after order %d", i)
          .isEqualTo(initialAsset);
    }
  }
}
