package dev.kaloyanyordanov.exchange.engine;

/**
 * A raw per-pair asset balance in an engine snapshot. Deliberately unvalidated
 * (unlike the ledger's {@code Account}) so the invariant checker can be tested
 * against deliberately corrupted snapshots (e.g. a negative asset). Cash is not
 * part of an engine's state in the multi-pair model; it lives in the shared cash
 * ledger and is checked by the cash-conservation checker instead.
 *
 * @param accountId the account id
 * @param asset     the asset balance for this engine's pair
 */
public record AccountBalance(long accountId, long asset) {}
