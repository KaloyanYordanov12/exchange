package dev.kaloyanyordanov.exchange.api;

/**
 * The caller's account balances.
 *
 * @param accountId the account id
 * @param cash      cash balance in scaled integer quote units
 * @param asset     asset balance in scaled integer units
 */
public record BalanceResponse(long accountId, long cash, long asset) {}
