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
 * Invariant 1 — deposit-aware cash conservation. Cash enters the system only
 * through deposits and leaves only through withdrawals; trades merely move it
 * between accounts. So over any random sequence of deposits, funded orders, and
 * withdrawals, total cash always equals {@code Σdeposits − Σwithdrawals} — never
 * changed by a fill.
 */
class CashConservationProperties {

  @Provide
  Arbitrary<LedgerFlows.Scenario> scenarios() {
    return LedgerFlows.scenarios();
  }

  @Property
  void totalCashEqualsDepositsMinusWithdrawals(
      @ForAll("scenarios") LedgerFlows.Scenario scenario) {
    Ledger ledger = new Ledger();
    long deposited = 0L;
    long withdrawn = 0L;

    // Fund cash through the deposit path; asset through a one-time genesis
    // endowment (there is no asset-deposit path — asset is conserved, while cash
    // flows in and out via deposits and withdrawals).
    for (int i = 0; i < LedgerFlows.ACCOUNTS; i++) {
      long cash = scenario.cash().get(i);
      if (cash > 0) {
        ledger.creditCash(i, cash);
        deposited += cash;
      }
      long asset = scenario.asset().get(i);
      if (asset > 0) {
        ledger.deposit(i, 0L, asset);
      }
      assertThat(ledger.totalCash())
          .as("total cash after funding account %d", i)
          .isEqualTo(deposited - withdrawn);
    }

    // Trades move cash between accounts but never change the total.
    OrderBook book = new OrderBook(LedgerFlows.SYMBOL);
    long tradeSequence = 0L;
    List<LedgerFlows.Spec> orders = scenario.orders();
    for (int i = 0; i < orders.size(); i++) {
      tradeSequence +=
          Matcher.match(book, LedgerFlows.toOrder(orders.get(i), i), ledger, tradeSequence).size();
      assertThat(ledger.totalCash())
          .as("total cash changed after trade %d", i)
          .isEqualTo(deposited - withdrawn);
    }

    // Withdrawals destroy cash; only successful ones (sufficient funds) count, and
    // the check-and-debit can never drive the total below what remains.
    for (int i = 0; i < LedgerFlows.ACCOUNTS; i++) {
      long amount = scenario.cash().get(i);
      if (amount > 0 && ledger.withdrawCash(i, amount)) {
        withdrawn += amount;
      }
      assertThat(ledger.totalCash())
          .as("total cash after withdrawing from account %d", i)
          .isEqualTo(deposited - withdrawn);
    }
  }
}
