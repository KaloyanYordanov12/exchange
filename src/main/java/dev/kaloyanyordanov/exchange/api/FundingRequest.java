package dev.kaloyanyordanov.exchange.api;

/**
 * A deposit or withdrawal request for the authenticated account.
 *
 * @param amount the amount in scaled integer quote units; must be positive
 */
public record FundingRequest(long amount) {}
