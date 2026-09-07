package dev.kaloyanyordanov.exchange.book;

import java.util.List;

/**
 * An immutable point-in-time view of the book: aggregated bid and ask levels,
 * bids highest-first and asks lowest-first. For later UI and persistence.
 *
 * @param bids aggregated bid levels, best (highest) first
 * @param asks aggregated ask levels, best (lowest) first
 */
public record BookSnapshot(List<PriceLevel> bids, List<PriceLevel> asks) {

  /** Defensively copies the level lists so the snapshot is truly immutable. */
  public BookSnapshot {
    bids = List.copyOf(bids);
    asks = List.copyOf(asks);
  }
}
