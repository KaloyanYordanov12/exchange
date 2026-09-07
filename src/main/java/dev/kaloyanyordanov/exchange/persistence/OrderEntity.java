package dev.kaloyanyordanov.exchange.persistence;

import dev.kaloyanyordanov.exchange.book.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only audit record of an accepted order. */
@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @Column(name = "order_id")
  private long orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "side")
  private Side side;

  @Column(name = "price")
  private long price;

  @Column(name = "quantity")
  private long quantity;

  @Column(name = "account_id")
  private long accountId;

  @Column(name = "arrival_sequence")
  private long arrivalSequence;

  /** For JPA. */
  protected OrderEntity() {}

  /**
   * Creates an order audit record.
   *
   * @param orderId         the order id
   * @param side            the side
   * @param price           the limit price in ticks
   * @param quantity        the quantity in units
   * @param accountId       the owning account
   * @param arrivalSequence the arrival sequence
   */
  public OrderEntity(
      long orderId, Side side, long price, long quantity, long accountId, long arrivalSequence) {
    this.orderId = orderId;
    this.side = side;
    this.price = price;
    this.quantity = quantity;
    this.accountId = accountId;
    this.arrivalSequence = arrivalSequence;
  }

  /**
   * The order id.
   *
   * @return the order id
   */
  public long getOrderId() {
    return orderId;
  }

  /**
   * The side.
   *
   * @return the side
   */
  public Side getSide() {
    return side;
  }

  /**
   * The limit price in ticks.
   *
   * @return the price
   */
  public long getPrice() {
    return price;
  }

  /**
   * The quantity in units.
   *
   * @return the quantity
   */
  public long getQuantity() {
    return quantity;
  }

  /**
   * The owning account.
   *
   * @return the account id
   */
  public long getAccountId() {
    return accountId;
  }

  /**
   * The arrival sequence.
   *
   * @return the arrival sequence
   */
  public long getArrivalSequence() {
    return arrivalSequence;
  }
}
