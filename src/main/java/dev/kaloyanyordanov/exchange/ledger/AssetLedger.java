package dev.kaloyanyordanov.exchange.ledger;

import dev.kaloyanyordanov.exchange.book.FillPolicy;
import dev.kaloyanyordanov.exchange.book.Trade;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The per-pair <b>asset</b> ledger, owned solely by one engine's matching thread.
 * In the multi-pair model an account's asset holdings are per-pair (its BTC lives
 * in the BTC engine, its ETH in the ETH engine), so — unlike cash — asset never
 * crosses engines and is safe as plain single-thread state, exactly like the
 * original combined ledger's asset half.
 *
 * <p>As a {@link FillPolicy} it caps the <em>seller</em> to the asset it holds
 * (so a settlement can never drive an asset balance negative) and settles the asset
 * leg on each fill. The <em>buyer</em> is unconstrained here: its cash was already
 * reserved in the shared {@link CashLedger} before the order entered the book, so
 * affordability is guaranteed and is never read cross-thread. The cash leg of each
 * fill is settled by the engine through the {@code CashLedger}, not here.
 */
public final class AssetLedger implements FillPolicy {

  private final Map<Long, Long> holdings = new HashMap<>();

  /**
   * Endows an account with opening asset (a one-time genesis endowment). Applied
   * before the engine starts.
   *
   * @param account the account
   * @param amount  the asset units to add; must be non-negative
   */
  public void endow(long account, long amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("endowment must be non-negative: " + amount);
    }
    holdings.merge(account, amount, Math::addExact);
  }

  /**
   * The asset balance of an account (zero if unknown).
   *
   * @param account the account
   * @return the asset balance
   */
  public long assetOf(long account) {
    return holdings.getOrDefault(account, 0L);
  }

  /**
   * The set of known account ids.
   *
   * @return an immutable copy of the account ids
   */
  public Set<Long> accountIds() {
    return Set.copyOf(holdings.keySet());
  }

  /**
   * Total asset across all accounts — invariant under settlement.
   *
   * @return the summed asset
   */
  public long totalAsset() {
    long total = 0L;
    for (long amount : holdings.values()) {
      total = Math.addExact(total, amount);
    }
    return total;
  }

  @Override
  public long maxBuyerUnits(long buyerAccountId, long tradePrice) {
    if (tradePrice <= 0) {
      throw new IllegalArgumentException("trade price must be positive: " + tradePrice);
    }
    // Buying power was reserved in the cash ledger before the order entered the
    // book, so the buyer is unconstrained by this per-pair asset ledger.
    return Long.MAX_VALUE;
  }

  @Override
  public long maxSellerUnits(long sellerAccountId) {
    return assetOf(sellerAccountId);
  }

  @Override
  public void onFill(Trade trade) {
    long quantity = trade.quantity();
    // The seller was capped to its holdings, so this never goes negative.
    holdings.merge(trade.sellerAccountId(), -quantity, Math::addExact);
    holdings.merge(trade.buyerAccountId(), quantity, Math::addExact);
  }
}
