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
 * Invariant 3 — no negative balances. Across any random sequence, including
 * under-funded orders that can only partially fill (or not at all), no account's
 * cash or asset ever goes below zero — checked after every matching step, so the
 * final unit against an under-funded order is covered too.
 */
class NoNegativeBalanceProperties {

  @Provide
  Arbitrary<LedgerFlows.Scenario> scenarios() {
    return LedgerFlows.scenarios();
  }

  @Property
  void noBalanceEverGoesNegative(@ForAll("scenarios") LedgerFlows.Scenario scenario) {
    Ledger ledger = LedgerFlows.fund(scenario);
    OrderBook book = new OrderBook(LedgerFlows.SYMBOL);
    long tradeSequence = 0L;
    List<LedgerFlows.Spec> orders = scenario.orders();

    for (int i = 0; i < orders.size(); i++) {
      tradeSequence +=
          Matcher.match(book, LedgerFlows.toOrder(orders.get(i), i), ledger, tradeSequence).size();
      assertAllBalancesNonNegative(ledger, i);
    }
    assertAllBalancesNonNegative(ledger, orders.size());
  }

  private static void assertAllBalancesNonNegative(Ledger ledger, int step) {
    for (int account = 0; account < LedgerFlows.ACCOUNTS; account++) {
      assertThat(ledger.cashOf(account))
          .as("account %d cash negative after step %d", account, step)
          .isGreaterThanOrEqualTo(0L);
      assertThat(ledger.assetOf(account))
          .as("account %d asset negative after step %d", account, step)
          .isGreaterThanOrEqualTo(0L);
    }
  }
}
