package dev.kaloyanyordanov.exchange.api;

/**
 * Response for an accepted order submission. "Accepted" means enqueued for
 * matching; the fill outcome is observed via the event stream, not this response.
 *
 * @param orderId the assigned order id
 * @param status  the placement status ({@code ACCEPTED})
 */
public record OrderResponse(long orderId, String status) {}
