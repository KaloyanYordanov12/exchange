package dev.kaloyanyordanov.exchange.engine;

/**
 * A raw account balance in a snapshot. Deliberately unvalidated (unlike the
 * ledger's {@code Account}) so the invariant checker can be tested against
 * deliberately corrupted snapshots — e.g. a negative balance.
 *
 * @param accountId the account id
 * @param cash      the cash balance
 * @param asset     the asset balance
 */
public record AccountBalance(long accountId, long cash, long asset) {}
