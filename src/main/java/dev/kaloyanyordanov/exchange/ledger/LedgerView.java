package dev.kaloyanyordanov.exchange.ledger;

import java.util.Set;

/**
 * A read-only view of the whole ledger, used by the engine to build a consistent
 * snapshot on the matching thread (the ledger's owner). Reads happen only on that
 * thread, so the ledger is never exposed to off-thread access.
 */
public interface LedgerView extends AccountView {

  /**
   * The set of known account ids.
   *
   * @return the account ids
   */
  Set<Long> accountIds();

  /**
   * An immutable snapshot of one account.
   *
   * @param accountId the account
   * @return the account snapshot
   */
  Account account(long accountId);

  /**
   * Total cash across all accounts.
   *
   * @return the summed cash
   */
  long totalCash();

  /**
   * Total asset across all accounts.
   *
   * @return the summed asset
   */
  long totalAsset();
}
