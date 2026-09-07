package dev.kaloyanyordanov.exchange.engine;

/**
 * Emitted on the matching thread after settlement, carrying an affected account's
 * new balances. Downstream read models and persistence consume this rather than
 * ever reading the ledger off-thread.
 *
 * @param accountId the account
 * @param cash      the new cash balance in scaled integer quote units
 * @param asset     the new asset balance in scaled integer units
 */
public record AccountUpdated(long accountId, long cash, long asset) implements EngineEvent {

  /** Validates the balances. */
  public AccountUpdated {
    if (cash < 0) {
      throw new IllegalArgumentException("cash must not be negative: " + cash);
    }
    if (asset < 0) {
      throw new IllegalArgumentException("asset must not be negative: " + asset);
    }
  }
}
