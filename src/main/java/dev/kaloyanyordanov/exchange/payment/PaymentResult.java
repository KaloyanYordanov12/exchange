package dev.kaloyanyordanov.exchange.payment;

/**
 * The outcome of a payment-provider operation.
 *
 * @param success   whether the provider accepted the movement
 * @param reference the provider's reference for the movement (for audit)
 */
public record PaymentResult(boolean success, String reference) {

  /** A reference is required. */
  public PaymentResult {
    if (reference == null) {
      throw new IllegalArgumentException("reference must be provided");
    }
  }
}
