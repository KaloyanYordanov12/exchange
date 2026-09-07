package dev.kaloyanyordanov.exchange.book;

/**
 * Identity of an order. A thin wrapper over a {@code long} so order references
 * are type-safe and never confused with prices, quantities, or account ids.
 *
 * @param value the non-negative identifier
 */
public record OrderId(long value) {

  /** Validates the identifier. */
  public OrderId {
    if (value < 0) {
      throw new IllegalArgumentException("order id must be non-negative: " + value);
    }
  }

  /**
   * Creates an order id.
   *
   * @param value the non-negative identifier
   * @return the order id
   */
  public static OrderId of(long value) {
    return new OrderId(value);
  }
}
