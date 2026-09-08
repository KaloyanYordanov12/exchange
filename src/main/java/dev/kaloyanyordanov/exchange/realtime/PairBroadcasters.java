package dev.kaloyanyordanov.exchange.realtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns one {@link ThrottledBroadcaster} per pair and their shared flush lifecycle.
 * Each pair's engine publishes only into its own broadcaster, and a WebSocket client
 * subscribes to exactly one pair (see {@link MarketDataWebSocketHandler}), so the five
 * pairs' market data never mix on the wire.
 *
 * <p>This is pure egress: the per-pair broadcasters are downstream event sinks, never
 * the matching core or any ledger. Making the egress pair-scoped adds no shared mutable
 * state to a hot path (each broadcaster is independent), so the single-threaded core and
 * the no-shared-mutable-state rule are untouched.
 */
public final class PairBroadcasters {

  private final Map<String, ThrottledBroadcaster> byPair = new LinkedHashMap<>();

  /**
   * Builds one broadcaster per pair id, all with the same throttle settings.
   *
   * @param pairIds           the pair ids to serve
   * @param serializer        the JSON serializer
   * @param bookHertz         book snapshot flush rate in Hz
   * @param maxTradesPerFlush the maximum trades emitted per flush
   * @param tapeCapacity      the bounded trade-tape capacity (power of two)
   */
  public PairBroadcasters(
      List<String> pairIds,
      JsonSerializer serializer,
      int bookHertz,
      int maxTradesPerFlush,
      int tapeCapacity) {
    for (String pairId : pairIds) {
      byPair.put(
          pairId, new ThrottledBroadcaster(pairId, serializer, bookHertz, maxTradesPerFlush,
              tapeCapacity));
    }
  }

  /**
   * The broadcaster for a pair, or {@code null} if the pair is unknown.
   *
   * @param pairId the pair id
   * @return the pair's broadcaster, or {@code null}
   */
  public ThrottledBroadcaster forPair(String pairId) {
    return byPair.get(pairId);
  }

  /**
   * The broadcasters, in pair order.
   *
   * @return the broadcasters
   */
  public List<ThrottledBroadcaster> all() {
    return new ArrayList<>(byPair.values());
  }

  /** Starts every pair's flush scheduler. */
  public void start() {
    for (ThrottledBroadcaster broadcaster : byPair.values()) {
      broadcaster.start();
    }
  }

  /** Stops every pair's flush scheduler. */
  public void stop() {
    for (ThrottledBroadcaster broadcaster : byPair.values()) {
      broadcaster.stop();
    }
  }
}
