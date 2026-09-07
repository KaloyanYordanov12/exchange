package dev.kaloyanyordanov.exchange.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Current-balances snapshot for an account (upserted from {@code AccountUpdated}). */
@Entity
@Table(name = "accounts")
public class AccountEntity {

  @Id
  @Column(name = "account_id")
  private long accountId;

  @Column(name = "cash")
  private long cash;

  @Column(name = "asset")
  private long asset;

  /** For JPA. */
  protected AccountEntity() {}

  /**
   * Creates an account snapshot.
   *
   * @param accountId the account id
   * @param cash      the cash balance
   * @param asset     the asset balance
   */
  public AccountEntity(long accountId, long cash, long asset) {
    this.accountId = accountId;
    this.cash = cash;
    this.asset = asset;
  }

  /**
   * The account id.
   *
   * @return the account id
   */
  public long getAccountId() {
    return accountId;
  }

  /**
   * The cash balance.
   *
   * @return the cash balance
   */
  public long getCash() {
    return cash;
  }

  /**
   * The asset balance.
   *
   * @return the asset balance
   */
  public long getAsset() {
    return asset;
  }
}
