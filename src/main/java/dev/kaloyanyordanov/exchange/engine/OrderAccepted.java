package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;

/**
 * Emitted when the matching thread accepts an order for matching and assigns its
 * arrival sequence. Carries the full order so the audit log can record it. The
 * sequence is the order's position in the deterministic processing order.
 *
 * @param id        the accepted order
 * @param side      buy or sell
 * @param price     the limit price in scaled integer ticks
 * @param quantity  the quantity in scaled integer units
 * @param accountId the owning account
 * @param sequence  the assigned arrival sequence
 */
public record OrderAccepted(
    OrderId id, Side side, long price, long quantity, long accountId, long sequence)
    implements EngineEvent {

  /** Validates the event. */
  public OrderAccepted {
    if (id == null) {
      throw new IllegalArgumentException("order id must be provided");
    }
    if (side == null) {
      throw new IllegalArgumentException("side must be provided");
    }
    if (price <= 0) {
      throw new IllegalArgumentException("price must be positive: " + price);
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive: " + quantity);
    }
    if (sequence < 0) {
      throw new IllegalArgumentException("sequence must be non-negative: " + sequence);
    }
  }
}
