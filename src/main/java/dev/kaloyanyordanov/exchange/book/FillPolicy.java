package dev.kaloyanyordanov.exchange.book;

/**
 * The hook through which matching consults and updates external state (the
 * ledger, from Phase 3) without the {@code book} package depending on it.
 *
 * <p>Before each fill the matcher caps the quantity to what both sides can bear:
 * the buyer's cash and the seller's asset. After each fill it calls
 * {@link #onFill(Trade)} so settlement is applied <em>immediately and serially</em>
 * on the matching thread — which is why the next affordability check already
 * sees the updated balances, and why no balance can be driven negative.
 *
 * <p>Phase 1 uses {@link #UNCONSTRAINED}: no caps, no settlement — pure matching.
 */
public interface FillPolicy {

  /**
   * The maximum whole units the buyer can afford at a price, i.e.
   * {@code floor(buyerCash / tradePrice)}.
   *
   * @param buyerAccountId the buyer's account
   * @param tradePrice     the execution price in ticks (positive)
   * @return the affordable unit cap; {@link Long#MAX_VALUE} if unconstrained
   */
  long maxBuyerUnits(long buyerAccountId, long tradePrice);

  /**
   * The maximum whole units the seller can deliver, i.e. the seller's asset
   * balance.
   *
   * @param sellerAccountId the seller's account
   * @return the deliverable unit cap; {@link Long#MAX_VALUE} if unconstrained
   */
  long maxSellerUnits(long sellerAccountId);

  /**
   * Applies settlement for a fill, immediately and on the matching thread.
   *
   * @param trade the executed fill
   */
  void onFill(Trade trade);

  /** No affordability limits and no settlement — pure Phase 1 matching. */
  FillPolicy UNCONSTRAINED =
      new FillPolicy() {
        @Override
        public long maxBuyerUnits(long buyerAccountId, long tradePrice) {
          return Long.MAX_VALUE;
        }

        @Override
        public long maxSellerUnits(long sellerAccountId) {
          return Long.MAX_VALUE;
        }

        @Override
        public void onFill(Trade trade) {
          // No settlement in Phase 1.
        }
      };
}
