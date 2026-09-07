package dev.kaloyanyordanov.exchange.ledger;

/** The outcome of a buying-power reservation processed by the cash-ledger thread. */
public enum ReservationOutcome {
  /** The cash was reserved (moved from available to reserved). */
  RESERVED,
  /** The account had insufficient available cash; nothing was reserved. */
  INSUFFICIENT_FUNDS,
  /** The request could not be processed (queue full or timed out). */
  UNAVAILABLE
}
