package dev.kaloyanyordanov.exchange.engine;

/**
 * A command to credit an account's cash (a deposit), applied serially on the
 * matching thread like a fill. Self-validating so it can never throw on the
 * matching thread.
 *
 * @param accountId         the account to credit
 * @param amount            the amount in scaled integer quote units; must be positive
 * @param providerReference the payment provider's reference for the deposit
 */
public record DepositCash(long accountId, long amount, String providerReference)
    implements Command {

  /** Validates the command. */
  public DepositCash {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    if (providerReference == null) {
      throw new IllegalArgumentException("provider reference must be provided");
    }
  }
}
