package dev.kaloyanyordanov.exchange.ledger;

/**
 * The ledger operations the engine performs on the matching thread: reading state
 * (via {@link LedgerView}) and applying serial cash movements for deposits and
 * withdrawals. Because these run only on the matching thread, check-then-commit is
 * atomic without a lock — a withdrawal can never race a fill or drive cash
 * negative.
 */
public interface AccountLedger extends LedgerView {

  /**
   * Credits an account with cash (a deposit). Applied on the matching thread.
   *
   * @param accountId the account
   * @param amount    the cash to credit; must be positive
   */
  void creditCash(long accountId, long amount);

  /**
   * Debits an account's cash (a withdrawal) if it has at least {@code amount}.
   * Applied on the matching thread, so the check and debit are atomic.
   *
   * @param accountId the account
   * @param amount    the cash to debit; must be positive
   * @return {@code true} if debited, {@code false} if insufficient cash
   */
  boolean withdrawCash(long accountId, long amount);
}
