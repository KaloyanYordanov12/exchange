package dev.kaloyanyordanov.exchange.engine;

/**
 * A command to withdraw an account's cash, applied serially on the matching
 * thread: the sufficient-funds check and debit are atomic there. The
 * {@code requestId} lets the requester collect the per-request outcome without a
 * shared future.
 *
 * @param requestId         a monotonic id identifying this withdrawal request
 * @param accountId         the account to debit
 * @param amount            the amount in scaled integer quote units; must be positive
 * @param providerReference the reference recorded for the withdrawal
 */
public record WithdrawCash(long requestId, long accountId, long amount, String providerReference)
    implements Command {

  /** Validates the command. */
  public WithdrawCash {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    if (providerReference == null) {
      throw new IllegalArgumentException("provider reference must be provided");
    }
  }
}
