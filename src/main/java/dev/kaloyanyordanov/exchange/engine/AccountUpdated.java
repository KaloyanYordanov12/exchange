package dev.kaloyanyordanov.exchange.engine;

/**
 * Emitted on a matching thread after settlement, carrying an affected account's new
 * <b>asset</b> balance for that engine's pair. Cash is not carried here: in the
 * multi-pair model cash is owned by the shared cash ledger, not by any engine, so
 * an engine only ever knows and publishes its own pair's asset. Downstream read
 * models and persistence consume this rather than reading a ledger off-thread.
 *
 * @param accountId the account
 * @param asset     the new asset balance in scaled integer units
 */
public record AccountUpdated(long accountId, long asset) implements EngineEvent {

  /** Validates the balance. */
  public AccountUpdated {
    if (asset < 0) {
      throw new IllegalArgumentException("asset must not be negative: " + asset);
    }
  }
}
