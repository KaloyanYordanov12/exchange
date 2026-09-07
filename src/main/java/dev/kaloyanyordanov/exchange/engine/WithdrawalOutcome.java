package dev.kaloyanyordanov.exchange.engine;

/** The outcome of a withdrawal request processed by the matching thread. */
public enum WithdrawalOutcome {
  /** The withdrawal was applied (debited). */
  APPLIED,
  /** The account had insufficient cash; nothing was debited. */
  INSUFFICIENT_FUNDS,
  /** The request could not be processed (queue full or timed out). */
  UNAVAILABLE
}
