package dev.kaloyanyordanov.exchange.ledger;

import java.util.List;

/**
 * An immutable, internally-consistent view of the whole cash ledger, captured on
 * the cash-ledger thread between commands. It carries everything the cross-account
 * cash-conservation invariant needs, so the checker never touches the live ledger.
 *
 * <p>The deposit-aware conservation law across all pairs is
 * {@code Σ(available + reserved) == cumulativeDeposited − cumulativeWithdrawn}:
 * cash is created only by deposits and destroyed only by withdrawals; reservations,
 * releases, and settlements only move it between an account's available/reserved
 * buckets and between accounts.
 *
 * @param accounts             every account's cash split (raw, so a corrupted
 *     snapshot can be detected in tests)
 * @param cumulativeDeposited  total cash deposited over the ledger's life
 * @param cumulativeWithdrawn  total cash withdrawn over the ledger's life
 */
public record CashSnapshot(
    List<CashAccount> accounts, long cumulativeDeposited, long cumulativeWithdrawn) {

  /** Defensively copies the account list so the snapshot is truly immutable. */
  public CashSnapshot {
    accounts = List.copyOf(accounts);
  }

  /**
   * The total available cash across all accounts.
   *
   * @return the summed available cash
   */
  public long totalAvailable() {
    long total = 0L;
    for (CashAccount account : accounts) {
      total = Math.addExact(total, account.available());
    }
    return total;
  }

  /**
   * The total reserved cash across all accounts.
   *
   * @return the summed reserved cash
   */
  public long totalReserved() {
    long total = 0L;
    for (CashAccount account : accounts) {
      total = Math.addExact(total, account.reserved());
    }
    return total;
  }

  /**
   * The total cash in the system (available plus reserved, all accounts).
   *
   * @return the summed total cash
   */
  public long totalCash() {
    return Math.addExact(totalAvailable(), totalReserved());
  }
}
