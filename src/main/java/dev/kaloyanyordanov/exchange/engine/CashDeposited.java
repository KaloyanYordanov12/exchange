package dev.kaloyanyordanov.exchange.engine;

/**
 * Emitted on the matching thread when a deposit is credited. Audited to the
 * append-only ledger-transactions log.
 *
 * @param accountId       the credited account
 * @param amount          the deposited amount in scaled integer quote units
 * @param newCashBalance  the account's cash balance after the credit
 * @param providerReference the payment provider's reference
 */
public record CashDeposited(
    long accountId, long amount, long newCashBalance, String providerReference)
    implements EngineEvent {

  /** Validates the event. */
  public CashDeposited {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    if (providerReference == null) {
      throw new IllegalArgumentException("provider reference must be provided");
    }
  }
}
