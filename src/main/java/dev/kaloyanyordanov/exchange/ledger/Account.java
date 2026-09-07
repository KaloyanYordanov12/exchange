package dev.kaloyanyordanov.exchange.ledger;

/**
 * An immutable snapshot of one account's balances. Scaled integers only; neither
 * balance may be negative.
 *
 * @param id    the account identifier
 * @param cash  the cash balance in scaled integer quote units
 * @param asset the asset balance in scaled integer units
 */
public record Account(long id, long cash, long asset) {

  /** Validates the balances. */
  public Account {
    if (cash < 0) {
      throw new IllegalArgumentException("cash must not be negative: " + cash);
    }
    if (asset < 0) {
      throw new IllegalArgumentException("asset must not be negative: " + asset);
    }
  }
}
