package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.OrderId;

/**
 * Emitted when an order (or the unfillable remainder of one) is rejected by the
 * matching thread — for example when an account cannot fund the trade. Wired to
 * the affordability path in Phase 3.
 *
 * @param id       the rejected order
 * @param reason   why it was rejected
 * @param quantity the quantity that was rejected, in units
 */
public record OrderRejected(OrderId id, RejectReason reason, long quantity) implements EngineEvent {

  /** The reason an order was rejected. */
  public enum RejectReason {
    /** The buyer had insufficient cash to settle. */
    INSUFFICIENT_CASH,
    /** The seller had insufficient asset to deliver. */
    INSUFFICIENT_ASSET
  }

  /** Validates the event. */
  public OrderRejected {
    if (id == null) {
      throw new IllegalArgumentException("order id must be provided");
    }
    if (reason == null) {
      throw new IllegalArgumentException("reason must be provided");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("rejected quantity must be positive: " + quantity);
    }
  }
}
