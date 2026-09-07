package dev.kaloyanyordanov.exchange.payment;

/**
 * Initiates a deposit or withdrawal for an account, returning a result with a
 * provider reference. Amounts are scaled integers (never floating point).
 *
 * <p>This is the seam a real adapter (a card processor, a crypto rail, ...) would
 * implement. In this build the only implementation is {@link DemoPaymentProvider};
 * a real one is deliberately out of scope. See its class comment.
 */
public interface PaymentProvider {

  /**
   * Initiates a deposit of {@code amount} for {@code accountId}.
   *
   * @param accountId the account
   * @param amount    the amount in scaled integer quote units (must be positive)
   * @return the provider result
   */
  PaymentResult initiateDeposit(long accountId, long amount);

  /**
   * Initiates a withdrawal of {@code amount} for {@code accountId}.
   *
   * @param accountId the account
   * @param amount    the amount in scaled integer quote units (must be positive)
   * @return the provider result
   */
  PaymentResult initiateWithdrawal(long accountId, long amount);
}
