package dev.kaloyanyordanov.exchange.engine;

/**
 * Emitted on the matching thread when a withdrawal is debited. Audited to the
 * append-only ledger-transactions log.
 *
 * @param accountId         the debited account
 * @param amount            the withdrawn amount in scaled integer quote units
 * @param newCashBalance    the account's cash balance after the debit
 * @param providerReference the payment provider's reference
 */
public record CashWithdrawn(
    long accountId, long amount, long newCashBalance, String providerReference)
    implements EngineEvent {

  /** Validates the event. */
  public CashWithdrawn {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    if (newCashBalance < 0) {
      throw new IllegalArgumentException("balance must not be negative: " + newCashBalance);
    }
    if (providerReference == null) {
      throw new IllegalArgumentException("provider reference must be provided");
    }
  }
}
