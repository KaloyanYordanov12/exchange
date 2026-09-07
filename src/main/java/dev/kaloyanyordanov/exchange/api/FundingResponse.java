package dev.kaloyanyordanov.exchange.api;

/**
 * The result of a deposit or withdrawal.
 *
 * @param accountId the account moved
 * @param amount    the requested amount in scaled integer quote units
 * @param status    the funding status (e.g. {@code ACCEPTED}, {@code APPLIED},
 *     {@code INSUFFICIENT_FUNDS})
 * @param reference the payment provider's reference for the movement
 */
public record FundingResponse(long accountId, long amount, String status, String reference) {}
