package dev.kaloyanyordanov.exchange.ledger;

/** The outcome of a withdrawal processed by the cash-ledger thread. */
public enum WithdrawalResult {
  /** The withdrawal was applied (debited from available cash). */
  APPLIED,
  /** The account had insufficient available cash; nothing was debited. */
  INSUFFICIENT_FUNDS,
  /** The request could not be processed (queue full or timed out). */
  UNAVAILABLE
}
