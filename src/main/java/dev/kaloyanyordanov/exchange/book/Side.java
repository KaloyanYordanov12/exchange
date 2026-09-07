package dev.kaloyanyordanov.exchange.book;

/** Order side: a buyer bids, a seller offers. */
public enum Side {
  BUY,
  SELL;

  /**
   * Returns the opposite side.
   *
   * @return {@code SELL} for {@code BUY} and vice versa
   */
  public Side opposite() {
    return this == BUY ? SELL : BUY;
  }
}
