package dev.kaloyanyordanov.exchange.book;

/**
 * A tradable pair with its scaling rules. Prices are expressed in whole
 * {@code tickSize} increments and quantities in whole {@code lotSize} units, so
 * all arithmetic stays in scaled integers — never floating point.
 *
 * @param base     the base asset (e.g. {@code BTC})
 * @param quote    the quote asset (e.g. {@code USD})
 * @param tickSize the minimum price increment, in scaled integer ticks; must be positive
 * @param lotSize  the minimum quantity increment, in scaled integer units; must be positive
 */
public record Symbol(String base, String quote, long tickSize, long lotSize) {

  /** Validates the scaling rules. */
  public Symbol {
    if (base == null || base.isBlank()) {
      throw new IllegalArgumentException("base asset must be provided");
    }
    if (quote == null || quote.isBlank()) {
      throw new IllegalArgumentException("quote asset must be provided");
    }
    if (tickSize <= 0) {
      throw new IllegalArgumentException("tick size must be positive: " + tickSize);
    }
    if (lotSize <= 0) {
      throw new IllegalArgumentException("lot size must be positive: " + lotSize);
    }
  }

  /**
   * Reports whether a price is a whole multiple of the tick size.
   *
   * @param price the price in scaled integer ticks
   * @return {@code true} if the price aligns to the tick grid
   */
  public boolean isValidPrice(long price) {
    return price > 0 && price % tickSize == 0;
  }

  /**
   * Reports whether a quantity is a whole multiple of the lot size.
   *
   * @param quantity the quantity in scaled integer units
   * @return {@code true} if the quantity aligns to the lot grid
   */
  public boolean isValidQuantity(long quantity) {
    return quantity > 0 && quantity % lotSize == 0;
  }
}
