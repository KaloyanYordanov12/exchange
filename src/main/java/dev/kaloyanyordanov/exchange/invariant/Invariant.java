package dev.kaloyanyordanov.exchange.invariant;

/**
 * The correctness invariants. Six are per-pair, checked against an engine snapshot
 * by {@link InvariantChecker}: asset conservation, no negative balances, no
 * overfill, price-time priority, book not crossed, trades balance. Cash conservation
 * is cross-account (cash is shared across pairs) and checked over the whole cash
 * ledger by {@link CashInvariantChecker}, alongside no-negative-cash.
 */
public enum Invariant {
  /** Total cash (available + reserved) across all accounts equals deposited minus withdrawn. */
  CASH_CONSERVATION,
  /** Total asset across all accounts in a pair equals the initial total. */
  ASSET_CONSERVATION,
  /** No account's asset (per pair) or cash (available/reserved) is negative. */
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
