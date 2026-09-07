package dev.kaloyanyordanov.exchange.book;

/**
 * An aggregated price level in a book snapshot: the total remaining quantity
 * resting at one price. Scaled integers only.
 *
 * @param price    the level price in ticks
 * @param quantity the total remaining quantity in units at that price
 */
public record PriceLevel(long price, long quantity) {

  /** Validates the level. */
  public PriceLevel {
    if (price <= 0) {
      throw new IllegalArgumentException("price must be positive: " + price);
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive: " + quantity);
    }
  }
}
