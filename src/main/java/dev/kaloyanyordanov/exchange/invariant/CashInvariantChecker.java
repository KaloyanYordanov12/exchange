package dev.kaloyanyordanov.exchange.invariant;

import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the cross-account cash invariants over the whole shared cash ledger,
 * accounting for every engine's activity. Cash is not per-pair, so these are checked
 * here rather than in any single engine's {@link InvariantChecker}:
 *
 * <ul>
 *   <li><b>Cash conservation:</b> total cash (available + reserved, all accounts)
 *       equals {@code Σdeposited − Σwithdrawn}. Reservations, releases, and fill
 *       settlements only move cash between buckets and accounts; only deposits create
 *       it and only withdrawals destroy it.
 *   <li><b>No negative balances:</b> no account's available or reserved cash is
 *       negative.
 * </ul>
 *
 * <p>Pure and deterministic: a corrupted snapshot fails the relevant invariant.
 */
public final class CashInvariantChecker {

  private CashInvariantChecker() {}

  /**
   * Checks the cash invariants against a cash-ledger snapshot.
   *
   * @param snapshot the cash snapshot
   * @return the report
   */
  public static CheckReport check(CashSnapshot snapshot) {
    List<InvariantResult> results = new ArrayList<>();
    results.add(checkConservation(snapshot));
    results.add(checkNoNegative(snapshot));
    return CheckReport.of(results);
  }

  private static InvariantResult checkConservation(CashSnapshot snapshot) {
    long expected = snapshot.cumulativeDeposited() - snapshot.cumulativeWithdrawn();
    long total = snapshot.totalCash();
    if (total == expected) {
      return InvariantResult.pass(Invariant.CASH_CONSERVATION);
    }
    return InvariantResult.fail(
        Invariant.CASH_CONSERVATION,
        "total cash " + total + " != deposited " + snapshot.cumulativeDeposited()
            + " - withdrawn " + snapshot.cumulativeWithdrawn() + " (= " + expected + ")");
  }

  private static InvariantResult checkNoNegative(CashSnapshot snapshot) {
    for (CashAccount account : snapshot.accounts()) {
      if (account.available() < 0 || account.reserved() < 0) {
        return InvariantResult.fail(
            Invariant.NO_NEGATIVE_BALANCES,
            "account " + account.accountId() + " available=" + account.available()
                + " reserved=" + account.reserved());
      }
    }
    return InvariantResult.pass(Invariant.NO_NEGATIVE_BALANCES);
  }
}
