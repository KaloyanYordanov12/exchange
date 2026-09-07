package dev.kaloyanyordanov.exchange.book;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A single-symbol limit order book. Bids are held highest-price-first and asks
 * lowest-price-first; within a price level orders rest in FIFO arrival order,
 * giving correct price-time priority.
 *
 * <p><b>Not thread-safe by design.</b> The book is owned by exactly one thread
 * (the matching thread, from Phase 2 on). Its plain non-concurrent structures
 * are safe precisely because nothing else ever touches it — the single-threaded
 * ownership is the correctness guarantee, so this class must never be shared.
 */
public final class OrderBook {

  private final Symbol symbol;
  // Bids: highest price first. Asks: lowest price first. Both use firstEntry()
  // as "best", and an ArrayDeque per level preserves FIFO time priority.
  private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
  private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();

  /**
   * Creates an empty book for a symbol.
   *
   * @param symbol the traded symbol
   */
  public OrderBook(Symbol symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("symbol must be provided");
    }
    this.symbol = symbol;
  }

  /**
   * The symbol this book trades.
   *
   * @return the symbol
   */
  public Symbol symbol() {
    return symbol;
  }

  private TreeMap<Long, ArrayDeque<Order>> sideMap(Side side) {
    return side == Side.BUY ? bids : asks;
  }

  /**
   * Rests an order in the book at its price level (FIFO tail).
   *
   * @param order the order to rest; its remaining quantity must be positive
   */
  public void addResting(Order order) {
    if (order == null) {
      throw new IllegalArgumentException("order must be provided");
    }
    if (order.remaining() <= 0) {
      throw new IllegalArgumentException("cannot rest an order with no remaining quantity");
    }
    sideMap(order.side())
        .computeIfAbsent(order.price(), price -> new ArrayDeque<>())
        .addLast(order);
  }

  /**
   * The best (highest) resting bid order, i.e. the head of the highest bid level.
   *
   * @return the best bid, or empty if there are no bids
   */
  public Optional<Order> bestBid() {
    return bestOrder(Side.BUY);
  }

  /**
   * The best (lowest) resting ask order, i.e. the head of the lowest ask level.
   *
   * @return the best ask, or empty if there are no asks
   */
  public Optional<Order> bestAsk() {
    return bestOrder(Side.SELL);
  }

  /**
   * The head order of the best price level on a side.
   *
   * @param side the side to inspect
   * @return the best resting order on that side, or empty
   */
  Optional<Order> bestOrder(Side side) {
    Map.Entry<Long, ArrayDeque<Order>> entry = sideMap(side).firstEntry();
    return entry == null ? Optional.empty() : Optional.of(entry.getValue().getFirst());
  }

  /**
   * Replaces the head order of the best level on a side with a reduced copy
   * (same price, smaller remaining), keeping its FIFO head position.
   *
   * @param side    the side to modify
   * @param reduced the reduced order to reinstate at the head
   */
  void replaceBest(Side side, Order reduced) {
    ArrayDeque<Order> level = sideMap(side).firstEntry().getValue();
    level.removeFirst();
    level.addFirst(reduced);
  }

  /**
   * Removes the head order of the best level on a side, dropping the level if it
   * becomes empty.
   *
   * @param side the side to modify
   */
  void removeBest(Side side) {
    TreeMap<Long, ArrayDeque<Order>> map = sideMap(side);
    Map.Entry<Long, ArrayDeque<Order>> entry = map.firstEntry();
    ArrayDeque<Order> level = entry.getValue();
    level.removeFirst();
    if (level.isEmpty()) {
      map.remove(entry.getKey());
    }
  }

  /**
   * Whether the book is crossed (a bid priced at or above an ask). A correct
   * book is never crossed after a match completes.
   *
   * @return {@code true} if best bid price &gt;= best ask price
   */
  public boolean isCrossed() {
    Optional<Order> bid = bestBid();
    Optional<Order> ask = bestAsk();
    return bid.isPresent() && ask.isPresent() && bid.get().price() >= ask.get().price();
  }

  /**
   * Whether the book holds no orders.
   *
   * @return {@code true} if both sides are empty
   */
  public boolean isEmpty() {
    return bids.isEmpty() && asks.isEmpty();
  }

  /**
   * An immutable aggregated snapshot: bids best-first, asks best-first.
   *
   * @return the snapshot
   */
  public BookSnapshot snapshot() {
    return new BookSnapshot(aggregate(bids), aggregate(asks));
  }

  private static List<PriceLevel> aggregate(TreeMap<Long, ArrayDeque<Order>> side) {
    List<PriceLevel> levels = new ArrayList<>(side.size());
    for (Map.Entry<Long, ArrayDeque<Order>> entry : side.entrySet()) {
      long total = 0L;
      for (Order order : entry.getValue()) {
        total += order.remaining();
      }
      levels.add(new PriceLevel(entry.getKey(), total));
    }
    return levels;
  }
}
