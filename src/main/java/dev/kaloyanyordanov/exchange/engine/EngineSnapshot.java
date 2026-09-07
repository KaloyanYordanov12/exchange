package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import java.util.List;

/**
 * An immutable, internally-consistent view of the engine's state, captured on the
 * matching thread between commands. It carries everything the invariant checker
 * needs, so the checker never touches the live book or ledger (§4.4). Balances and
 * resting orders are raw records so the checker can be tested against corrupted
 * snapshots.
 *
 * @param book                       aggregated book levels (best-first per side)
 * @param restingOrders              every resting order (for overfill/FIFO checks)
 * @param accounts                   every account's balances
 * @param initialTotalCash           total cash at engine start
 * @param initialTotalAsset          total asset at engine start
 * @param cumulativeCashFromBuyers   cumulative cash debited from buyers
 * @param cumulativeCashToSellers    cumulative cash credited to sellers
 * @param cumulativeAssetFromSellers cumulative asset debited from sellers
 * @param cumulativeAssetToBuyers    cumulative asset credited to buyers
 */
public record EngineSnapshot(
    BookSnapshot book,
    List<RestingOrder> restingOrders,
    List<AccountBalance> accounts,
    long initialTotalCash,
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
