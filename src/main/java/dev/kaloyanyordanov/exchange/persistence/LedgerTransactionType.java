package dev.kaloyanyordanov.exchange.persistence;

/** The kind of cash movement recorded in the append-only ledger-transactions log. */
public enum LedgerTransactionType {
  /** Cash credited to an account. */
  DEPOSIT,
  /** Cash debited from an account. */
  WITHDRAWAL
}
