package dev.kaloyanyordanov.exchange.ledger;

/**
 * An immutable snapshot of one account's cash, split into freely spendable and
 * order-committed (reserved) portions. Both are scaled integers.
 *
 * @param accountId the account
 * @param available cash free to spend or withdraw
 * @param reserved  cash held against open buy orders (buying power)
 */
public record CashAccount(long accountId, long available, long reserved) {

  /**
   * The account's total cash (available plus reserved).
   *
   * @return the total cash
   */
  public long total() {
    return Math.addExact(available, reserved);
  }
}
