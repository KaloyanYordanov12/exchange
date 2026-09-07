package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.OrderId;

/**
 * Emitted when the matching thread accepts an order for matching and assigns its
 * arrival sequence. The sequence is the order's position in the deterministic
 * processing order.
 *
 * @param id       the accepted order
 * @param sequence the assigned arrival sequence
 */
public record OrderAccepted(OrderId id, long sequence) implements EngineEvent {

  /** Validates the event. */
  public OrderAccepted {
    if (id == null) {
      throw new IllegalArgumentException("order id must be provided");
    }
    if (sequence < 0) {
      throw new IllegalArgumentException("sequence must be non-negative: " + sequence);
    }
  }
}
