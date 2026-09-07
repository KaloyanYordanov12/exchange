package dev.kaloyanyordanov.exchange.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only audit record of a fill, keyed by its unique execution sequence. */
@Entity
@Table(name = "trades")
public class TradeEntity {

  @Id
  @Column(name = "trade_sequence")
  private long tradeSequence;

  @Column(name = "buy_order_id")
  private long buyOrderId;

  @Column(name = "sell_order_id")
  private long sellOrderId;

  @Column(name = "price")
  private long price;

  @Column(name = "quantity")
  private long quantity;

  @Column(name = "buyer_account_id")
  private long buyerAccountId;

  @Column(name = "seller_account_id")
  private long sellerAccountId;

  /** For JPA. */
  protected TradeEntity() {}

  /**
   * Creates a trade audit record.
   *
   * @param tradeSequence    the execution sequence (unique)
   * @param buyOrderId       the buying order id
   * @param sellOrderId      the selling order id
   * @param price            the execution price in ticks
   * @param quantity         the filled quantity in units
   * @param buyerAccountId   the buyer's account
   * @param sellerAccountId  the seller's account
   */
  public TradeEntity(
      long tradeSequence,
      long buyOrderId,
      long sellOrderId,
      long price,
      long quantity,
      long buyerAccountId,
      long sellerAccountId) {
    this.tradeSequence = tradeSequence;
    this.buyOrderId = buyOrderId;
    this.sellOrderId = sellOrderId;
    this.price = price;
    this.quantity = quantity;
    this.buyerAccountId = buyerAccountId;
    this.sellerAccountId = sellerAccountId;
  }

  /**
   * The execution sequence.
   *
   * @return the trade sequence
   */
  public long getTradeSequence() {
    return tradeSequence;
  }

  /**
   * The buying order id.
   *
   * @return the buy order id
   */
  public long getBuyOrderId() {
    return buyOrderId;
  }

  /**
   * The selling order id.
   *
   * @return the sell order id
   */
  public long getSellOrderId() {
    return sellOrderId;
  }

  /**
   * The execution price in ticks.
   *
   * @return the price
   */
  public long getPrice() {
    return price;
  }

  /**
   * The filled quantity in units.
   *
   * @return the quantity
   */
  public long getQuantity() {
    return quantity;
  }

  /**
   * The buyer's account.
   *
   * @return the buyer account id
   */
  public long getBuyerAccountId() {
    return buyerAccountId;
  }

  /**
   * The seller's account.
   *
   * @return the seller account id
   */
  public long getSellerAccountId() {
    return sellerAccountId;
  }
}
