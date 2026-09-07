package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;

/**
 * A command to submit a new limit order. Self-validating so a malformed command
 * fails at the producer (construction) and never reaches — nor can throw on —
 * the matching thread. The arrival sequence is deliberately absent: it is
 * assigned by the matching thread when the command is processed, which is the
 * deterministic linearization point.
 *
 * @param id        the order identity
 * @param side      buy or sell
 * @param price     the limit price in scaled integer ticks; must be positive
 * @param quantity  the quantity in scaled integer units; must be positive
 * @param accountId the owning account
 */
public record SubmitOrder(OrderId id, Side side, long price, long quantity, long accountId)
    implements Command {

  /** Validates the command's scaled-integer fields. */
  public SubmitOrder {
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
  }
}
