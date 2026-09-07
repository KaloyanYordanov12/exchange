package dev.kaloyanyordanov.exchange.invariant;

/** The seven correctness invariants verified against an engine snapshot. */
public enum Invariant {
  /** Total cash across all accounts equals the initial total. */
  CASH_CONSERVATION,
  /** Total asset across all accounts equals the initial total. */
  ASSET_CONSERVATION,
  /** No account's cash or asset is negative. */
  NO_NEGATIVE_BALANCES,
  /** No resting order is overfilled (0 &lt; remaining &lt;= quantity). */
  NO_OVERFILL,
  /** Book ordering is intact: price priority across levels, time priority within. */
  PRICE_TIME_PRIORITY,
  /** The book is not crossed (best bid &lt; best ask). */
  BOOK_NOT_CROSSED,
  /** Cumulative trade debits equal credits, in cash and asset. */
  TRADES_BALANCE
}
