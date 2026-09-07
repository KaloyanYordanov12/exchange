package dev.kaloyanyordanov.exchange.ledger;

/**
 * A read-only view of account balances, used by the engine to publish balance
 * snapshots after settlement. Implemented by {@link Ledger}. Reads happen only
 * on the matching thread (the ledger's owner), so this never exposes the ledger
 * to off-thread access.
 */
public interface AccountView {

  /**
   * The cash balance of an account.
   *
   * @param accountId the account
   * @return the cash balance in scaled integer quote units
   */
  long cashOf(long accountId);

  /**
   * The asset balance of an account.
   *
   * @param accountId the account
   * @return the asset balance in scaled integer units
   */
  long assetOf(long accountId);
}
