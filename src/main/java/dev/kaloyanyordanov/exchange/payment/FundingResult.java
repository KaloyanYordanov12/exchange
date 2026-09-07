package dev.kaloyanyordanov.exchange.payment;

/**
 * The result of a funding operation: the {@link FundingStatus} and the provider
 * reference recorded for the movement (for audit).
 *
 * @param status    the outcome
 * @param reference the provider reference (always present; even a declined
 *     provider result carries a reference)
 */
public record FundingResult(FundingStatus status, String reference) {

  /** Validates the result. */
  public FundingResult {
    if (status == null) {
      throw new IllegalArgumentException("status must be provided");
    }
    if (reference == null) {
      throw new IllegalArgumentException("reference must be provided");
    }
  }
}
