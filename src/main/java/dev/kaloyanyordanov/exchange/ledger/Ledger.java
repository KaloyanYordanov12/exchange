package dev.kaloyanyordanov.exchange.ledger;

import dev.kaloyanyordanov.exchange.book.FillPolicy;
import dev.kaloyanyordanov.exchange.book.Trade;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-memory account balances, owned by the matching thread and settled
 * synchronously on each fill. Because every read and write happens serially on
 * that one thread, the check-then-commit is atomic without any lock, and the
 * ledger invariants — conservation of cash and asset, no negative balances, and
 * equal-and-opposite settlement — hold by construction.
 *
 * <p>As a {@link FillPolicy} it is the pre-trade check: {@link #maxBuyerUnits}
 * caps a fill to what the buyer can pay and {@link #maxSellerUnits} to what the
 * seller can deliver, so a settlement can never drive a balance negative.
 */
public final class Ledger implements FillPolicy {

  /** Mutable per-account position; mutated only on the matching thread. */
  private static final class Position {
    private long cash;
    private long asset;
  }

  private final Map<Long, Position> positions = new HashMap<>();

  /**
   * Credits an account with cash and/or asset (funding). Amounts must be
   * non-negative.
   *
   * @param accountId the account
   * @param cash      cash to add, in quote units
   * @param asset     asset units to add
   */
  public void deposit(long accountId, long cash, long asset) {
    if (cash < 0 || asset < 0) {
      throw new IllegalArgumentException("deposit amounts must be non-negative");
    }
    Position position = positions.computeIfAbsent(accountId, id -> new Position());
    position.cash = Math.addExact(position.cash, cash);
    position.asset = Math.addExact(position.asset, asset);
  }

  /**
   * The cash balance of an account (zero if unknown).
   *
   * @param accountId the account
   * @return the cash balance
   */
  public long cashOf(long accountId) {
    Position position = positions.get(accountId);
    return position == null ? 0L : position.cash;
  }

  /**
   * The asset balance of an account (zero if unknown).
   *
   * @param accountId the account
   * @return the asset balance
   */
  public long assetOf(long accountId) {
    Position position = positions.get(accountId);
    return position == null ? 0L : position.asset;
  }

  /**
   * An immutable snapshot of an account.
   *
   * @param accountId the account
   * @return the account snapshot
   */
  public Account account(long accountId) {
    return new Account(accountId, cashOf(accountId), assetOf(accountId));
  }

  /**
   * The set of known account ids.
   *
   * @return an immutable copy of the account ids
   */
  public Set<Long> accountIds() {
    return Set.copyOf(positions.keySet());
  }

  /**
   * Total cash across all accounts — invariant under settlement.
   *
   * @return the summed cash
   */
  public long totalCash() {
    long total = 0L;
    for (Position position : positions.values()) {
      total = Math.addExact(total, position.cash);
    }
    return total;
  }

  /**
   * Total asset across all accounts — invariant under settlement.
   *
   * @return the summed asset
   */
  public long totalAsset() {
    long total = 0L;
    for (Position position : positions.values()) {
      total = Math.addExact(total, position.asset);
    }
    return total;
  }

  @Override
  public long maxBuyerUnits(long buyerAccountId, long tradePrice) {
    if (tradePrice <= 0) {
      throw new IllegalArgumentException("trade price must be positive: " + tradePrice);
    }
    return cashOf(buyerAccountId) / tradePrice;
  }

  @Override
  public long maxSellerUnits(long sellerAccountId) {
    return assetOf(sellerAccountId);
  }

  @Override
  public void onFill(Trade trade) {
    long notional = Math.multiplyExact(trade.price(), trade.quantity());
    long quantity = trade.quantity();

    // Both positions exist and are sufficient: a fill only occurs when the caps
    // above were positive. Debits are applied before credits so that even a
    // self-trade never shows a transient negative balance.
    Position buyer = positions.get(trade.buyerAccountId());
    Position seller = positions.get(trade.sellerAccountId());

    buyer.cash = Math.subtractExact(buyer.cash, notional);
    seller.asset = Math.subtractExact(seller.asset, quantity);
    buyer.asset = Math.addExact(buyer.asset, quantity);
    seller.cash = Math.addExact(seller.cash, notional);
  }
}
