package dev.kaloyanyordanov.exchange.book;

/**
 * An immutable record of a single fill between a buy order and a sell order.
 * Executes at the resting (maker) price. All fields are scaled integers.
 *
 * @param buyOrderId       the buying order
 * @param sellOrderId      the selling order
 * @param price            the execution price in ticks (the maker's price)
 * @param quantity         the filled quantity in units; positive
 * @param buyerAccountId   the buyer's account
 * @param sellerAccountId  the seller's account
 * @param sequence         a monotonically increasing execution sequence
 */
public record Trade(
    OrderId buyOrderId,
    OrderId sellOrderId,
    long price,
    long quantity,
    long buyerAccountId,
    long sellerAccountId,
    long sequence) {

  /** Validates the fill. */
  public Trade {
    if (buyOrderId == null || sellOrderId == null) {
      throw new IllegalArgumentException("both order ids must be provided");
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

  /**
   * The total quote-currency value of this fill, {@code price * quantity}.
   *
   * @return the notional value in scaled integer quote units
   * @throws ArithmeticException if the value overflows {@code long}
   */
  public long notional() {
    return Math.multiplyExact(price, quantity);
  }
}
