package dev.kaloyanyordanov.exchange.ledger;

import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Shared generators for random <em>funded</em> trading scenarios: a fixed set of
 * accounts with random cash/asset deposits, and a random order flow over those
 * accounts. Deposits and prices are bounded so totals never overflow, and
 * because deposits are often small the flow naturally exercises under-funded
 * orders — the adversarial case for the no-negative-balance invariant.
 */
final class LedgerFlows {

  private LedgerFlows() {}

  static final int ACCOUNTS = 5;
  static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  /** A generated order intent over one of the funded accounts. */
  record Spec(Side side, long price, long quantity, long account) {}

  /**
   * A funded scenario: per-account cash and asset deposits (indexed by account
   * id 0..ACCOUNTS-1) plus a random order flow.
   *
   * @param cash   per-account cash deposits
   * @param asset  per-account asset deposits
   * @param orders the order flow
   */
  record Scenario(List<Long> cash, List<Long> asset, List<Spec> orders) {}

  /**
   * Random funded scenarios.
   *
   * @return an arbitrary of scenarios
   */
  static Arbitrary<Scenario> scenarios() {
    Arbitrary<List<Long>> cash =
        Arbitraries.longs().between(0L, 2_000L).list().ofSize(ACCOUNTS);
    Arbitrary<List<Long>> asset =
        Arbitraries.longs().between(0L, 200L).list().ofSize(ACCOUNTS);
    Arbitrary<Spec> spec =
        Combinators.combine(
                Arbitraries.of(Side.BUY, Side.SELL),
                Arbitraries.longs().between(1L, 20L),
                Arbitraries.longs().between(1L, 10L),
                Arbitraries.longs().between(0L, ACCOUNTS - 1L))
            .as(Spec::new);
    Arbitrary<List<Spec>> orders = spec.list().ofMinSize(0).ofMaxSize(50);
    return Combinators.combine(cash, asset, orders).as(Scenario::new);
  }

  /**
   * Builds a ledger funded per the scenario's deposits.
   *
   * @param scenario the scenario
   * @return the funded ledger
   */
  static Ledger fund(Scenario scenario) {
    Ledger ledger = new Ledger();
    for (int i = 0; i < ACCOUNTS; i++) {
      ledger.deposit(i, scenario.cash().get(i), scenario.asset().get(i));
    }
    return ledger;
  }

  /**
   * Turns a spec into an order using the flow index as id and arrival sequence.
   *
   * @param spec  the intent
   * @param index the submission index
   * @return the order
   */
  static Order toOrder(Spec spec, long index) {
    return Order.create(
        OrderId.of(index), spec.side(), spec.price(), spec.quantity(), index, spec.account());
  }
}
