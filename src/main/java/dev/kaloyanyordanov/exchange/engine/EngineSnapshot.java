package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import java.util.List;

/**
 * An immutable, internally-consistent view of one engine's state, captured on its
 * matching thread between commands. It carries everything the per-pair invariant
 * checker needs, so the checker never touches the live book or ledger. Balances and
 * resting orders are raw records so the checker can be tested against corrupted
 * snapshots.
 *
 * <p>Cash is not part of an engine's snapshot: in the multi-pair model cash is owned
 * by the shared cash ledger and verified by the cash-conservation checker. An engine
 * snapshot covers only its pair's book and asset holdings, plus the cumulative trade
 * flows used by the trades-balance invariant.
 *
 * @param book                       aggregated book levels (best-first per side)
 * @param restingOrders              every resting order (for overfill/FIFO checks)
 * @param accounts                   every account's asset balance for this pair
 * @param initialTotalAsset          total asset at engine start
 * @param cumulativeCashFromBuyers   cumulative cash debited from buyers (from trades)
 * @param cumulativeCashToSellers    cumulative cash credited to sellers (from trades)
 * @param cumulativeAssetFromSellers cumulative asset debited from sellers
 * @param cumulativeAssetToBuyers    cumulative asset credited to buyers
 */
public record EngineSnapshot(
    BookSnapshot book,
    List<RestingOrder> restingOrders,
    List<AccountBalance> accounts,
    long initialTotalAsset,
    long cumulativeCashFromBuyers,
    long cumulativeCashToSellers,
    long cumulativeAssetFromSellers,
    long cumulativeAssetToBuyers) {

  /** Defensively copies the lists so the snapshot is truly immutable. */
  public EngineSnapshot {
    restingOrders = List.copyOf(restingOrders);
    accounts = List.copyOf(accounts);
  }
}
