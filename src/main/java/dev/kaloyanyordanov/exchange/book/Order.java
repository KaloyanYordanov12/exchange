package dev.kaloyanyordanov.exchange.book;

/**
 * An immutable limit order. All monetary and size fields are scaled integers.
 *
 * <p>{@code remaining} is the unfilled quantity; it shrinks as the order fills
 * by producing a new {@link Order} via {@link #withRemaining(long)} — instances
 * are never mutated in place, which keeps matching deterministic and free of
 * shared mutable state.
 *
 * @param id        the order identity
 * @param side      buy or sell
 * @param price     the limit price in scaled integer ticks; must be positive
 * @param quantity  the original quantity in scaled integer units; must be positive
 * @param remaining the unfilled quantity; in {@code [0, quantity]}
 * @param sequence  a monotonically increasing arrival sequence for time priority
 * @param accountId the owning account
 */
public record Order(
    OrderId id,
    Side side,
    long price,
    long quantity,
    long remaining,
    long sequence,
    long accountId) {

  /** Validates the order's scaled-integer invariants. */
  public Order {
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
    if (remaining < 0 || remaining > quantity) {
      throw new IllegalArgumentException(
          "remaining must be in [0, quantity]: remaining=" + remaining + " quantity=" + quantity);
    }
    if (sequence < 0) {
      throw new IllegalArgumentException("sequence must be non-negative: " + sequence);
    }
  }

  /**
   * Creates a fresh, fully-unfilled order (remaining == quantity).
   *
   * @param id        the order identity
   * @param side      buy or sell
   * @param price     the limit price in ticks
   * @param quantity  the quantity in units
   * @param sequence  the arrival sequence
   * @param accountId the owning account
   * @return the new order
   */
  public static Order create(
      OrderId id, Side side, long price, long quantity, long sequence, long accountId) {
    return new Order(id, side, price, quantity, quantity, sequence, accountId);
  }

  /**
   * Returns a copy of this order with a new remaining quantity.
   *
   * @param newRemaining the new unfilled quantity, in {@code [0, quantity]}
   * @return a new order instance
   */
  public Order withRemaining(long newRemaining) {
    return new Order(id, side, price, quantity, newRemaining, sequence, accountId);
  }

  /**
   * The quantity already filled.
   *
   * @return {@code quantity - remaining}
   */
  public long filled() {
    return quantity - remaining;
  }

  /**
   * Whether the order has no remaining quantity.
   *
   * @return {@code true} if fully filled
   */
  public boolean isFullyFilled() {
    return remaining == 0;
  }
}
