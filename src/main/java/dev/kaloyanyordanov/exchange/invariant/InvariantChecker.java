package dev.kaloyanyordanov.exchange.invariant;

import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.engine.AccountBalance;
import dev.kaloyanyordanov.exchange.engine.EngineSnapshot;
import dev.kaloyanyordanov.exchange.engine.RestingOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the seven correctness invariants against an immutable
 * {@link EngineSnapshot}. Pure and deterministic: the same snapshot always yields
 * the same report, and a corrupted snapshot fails the relevant invariant — a
 * checker that could only ever say "green" would be worthless (§5).
 */
public final class InvariantChecker {

  private InvariantChecker() {}

  /**
   * Checks every invariant against a snapshot.
   *
   * @param snapshot the engine snapshot
   * @return the report
   */
  public static CheckReport check(EngineSnapshot snapshot) {
    List<InvariantResult> results = new ArrayList<>();
    results.add(checkCashConservation(snapshot));
    results.add(checkAssetConservation(snapshot));
    results.add(checkNoNegativeBalances(snapshot));
    results.add(checkNoOverfill(snapshot));
    results.add(checkPriceTimePriority(snapshot));
    results.add(checkBookNotCrossed(snapshot));
    results.add(checkTradesBalance(snapshot));
    return CheckReport.of(results);
  }

  private static InvariantResult checkCashConservation(EngineSnapshot snapshot) {
    long total = snapshot.accounts().stream().mapToLong(AccountBalance::cash).sum();
    if (total == snapshot.initialTotalCash()) {
      return InvariantResult.pass(Invariant.CASH_CONSERVATION);
    }
    return InvariantResult.fail(
        Invariant.CASH_CONSERVATION,
        "total cash " + total + " != initial " + snapshot.initialTotalCash());
  }

  private static InvariantResult checkAssetConservation(EngineSnapshot snapshot) {
    long total = snapshot.accounts().stream().mapToLong(AccountBalance::asset).sum();
    if (total == snapshot.initialTotalAsset()) {
      return InvariantResult.pass(Invariant.ASSET_CONSERVATION);
    }
    return InvariantResult.fail(
        Invariant.ASSET_CONSERVATION,
        "total asset " + total + " != initial " + snapshot.initialTotalAsset());
  }

  private static InvariantResult checkNoNegativeBalances(EngineSnapshot snapshot) {
    for (AccountBalance account : snapshot.accounts()) {
      if (account.cash() < 0 || account.asset() < 0) {
        return InvariantResult.fail(
            Invariant.NO_NEGATIVE_BALANCES,
            "account " + account.accountId() + " cash=" + account.cash()
                + " asset=" + account.asset());
      }
    }
    return InvariantResult.pass(Invariant.NO_NEGATIVE_BALANCES);
  }

  private static InvariantResult checkNoOverfill(EngineSnapshot snapshot) {
    for (RestingOrder order : snapshot.restingOrders()) {
      if (order.remaining() <= 0 || order.remaining() > order.quantity()) {
        return InvariantResult.fail(
            Invariant.NO_OVERFILL,
            "order " + order.orderId() + " remaining=" + order.remaining()
                + " quantity=" + order.quantity());
      }
    }
    return InvariantResult.pass(Invariant.NO_OVERFILL);
  }

  private static InvariantResult checkPriceTimePriority(EngineSnapshot snapshot) {
    List<PriceLevel> bids = snapshot.book().bids();
    for (int i = 1; i < bids.size(); i++) {
      if (bids.get(i - 1).price() <= bids.get(i).price()) {
        return InvariantResult.fail(
            Invariant.PRICE_TIME_PRIORITY, "bids not strictly descending at level " + i);
      }
    }
    List<PriceLevel> asks = snapshot.book().asks();
    for (int i = 1; i < asks.size(); i++) {
      if (asks.get(i - 1).price() >= asks.get(i).price()) {
        return InvariantResult.fail(
            Invariant.PRICE_TIME_PRIORITY, "asks not strictly ascending at level " + i);
      }
    }
    Map<String, Long> lastSequence = new HashMap<>();
    for (RestingOrder order : snapshot.restingOrders()) {
      String key = order.side() + ":" + order.price();
      Long previous = lastSequence.get(key);
      if (previous != null && order.sequence() < previous) {
        return InvariantResult.fail(
            Invariant.PRICE_TIME_PRIORITY,
            "FIFO broken at " + key + ": sequence " + order.sequence() + " after " + previous);
      }
      lastSequence.put(key, order.sequence());
    }
    return InvariantResult.pass(Invariant.PRICE_TIME_PRIORITY);
  }

  private static InvariantResult checkBookNotCrossed(EngineSnapshot snapshot) {
    List<PriceLevel> bids = snapshot.book().bids();
    List<PriceLevel> asks = snapshot.book().asks();
    if (!bids.isEmpty() && !asks.isEmpty() && bids.get(0).price() >= asks.get(0).price()) {
      return InvariantResult.fail(
          Invariant.BOOK_NOT_CROSSED,
          "best bid " + bids.get(0).price() + " >= best ask " + asks.get(0).price());
    }
    return InvariantResult.pass(Invariant.BOOK_NOT_CROSSED);
  }

  private static InvariantResult checkTradesBalance(EngineSnapshot snapshot) {
    if (snapshot.cumulativeCashFromBuyers() != snapshot.cumulativeCashToSellers()) {
      return InvariantResult.fail(
          Invariant.TRADES_BALANCE,
          "cash from buyers " + snapshot.cumulativeCashFromBuyers()
              + " != to sellers " + snapshot.cumulativeCashToSellers());
    }
    if (snapshot.cumulativeAssetFromSellers() != snapshot.cumulativeAssetToBuyers()) {
      return InvariantResult.fail(
          Invariant.TRADES_BALANCE,
          "asset from sellers " + snapshot.cumulativeAssetFromSellers()
              + " != to buyers " + snapshot.cumulativeAssetToBuyers());
    }
    return InvariantResult.pass(Invariant.TRADES_BALANCE);
  }
}
