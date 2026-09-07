package dev.kaloyanyordanov.exchange.payment;

/** The outcome of a deposit or withdrawal orchestrated by {@link PaymentService}. */
public enum FundingStatus {
  /** A deposit was authorized by the provider and enqueued for the serial credit. */
  ACCEPTED,
  /** A withdrawal was authorized and debited on the matching thread. */
  APPLIED,
  /** A withdrawal could not be applied: the account lacked the cash. Nothing debited. */
  INSUFFICIENT_FUNDS,
  /** The payment provider declined the movement (fail-secure). Nothing credited or debited. */
  PROVIDER_DECLINED,
  /** The engine could not process the request (busy, stopped, or timed out). */
  UNAVAILABLE
}
