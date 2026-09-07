package dev.kaloyanyordanov.exchange.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only audit record of one cash movement (a deposit or withdrawal). The id
 * is database-generated; the row is never updated or deleted. The balance after
 * the movement is recorded so the log is self-contained.
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type")
  private LedgerTransactionType transactionType;

  @Column(name = "account_id")
  private long accountId;

  @Column(name = "amount")
  private long amount;

  @Column(name = "new_cash_balance")
  private long newCashBalance;

  @Column(name = "provider_reference")
  private String providerReference;

  @Column(name = "created_at")
  private Instant createdAt;

  /** For JPA. */
  protected LedgerTransactionEntity() {}

  /**
   * Creates a cash-movement audit record.
   *
   * @param transactionType   deposit or withdrawal
   * @param accountId         the account moved
   * @param amount            the amount in scaled integer quote units
   * @param newCashBalance    the account's cash balance after the movement
   * @param providerReference the payment provider's reference
   * @param createdAt         when the record was written
   */
  public LedgerTransactionEntity(
      LedgerTransactionType transactionType,
      long accountId,
      long amount,
      long newCashBalance,
      String providerReference,
      Instant createdAt) {
    this.transactionType = transactionType;
    this.accountId = accountId;
    this.amount = amount;
    this.newCashBalance = newCashBalance;
    this.providerReference = providerReference;
    this.createdAt = createdAt;
  }

  /**
   * The generated id.
   *
   * @return the id, or {@code null} before it is persisted
   */
  public Long getId() {
    return id;
  }

  /**
   * The kind of movement.
   *
   * @return deposit or withdrawal
   */
  public LedgerTransactionType getTransactionType() {
    return transactionType;
  }

  /**
   * The account moved.
   *
   * @return the account id
   */
  public long getAccountId() {
    return accountId;
  }

  /**
   * The amount moved.
   *
   * @return the amount in scaled integer quote units
   */
  public long getAmount() {
    return amount;
  }

  /**
   * The cash balance after the movement.
   *
   * @return the new cash balance
   */
  public long getNewCashBalance() {
    return newCashBalance;
  }

  /**
   * The payment provider's reference.
   *
   * @return the provider reference
   */
  public String getProviderReference() {
    return providerReference;
  }

  /**
   * When the record was written.
   *
   * @return the creation timestamp
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
