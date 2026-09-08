package dev.kaloyanyordanov.exchange.candle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * The queryable candle series for every pair and timeframe, kept in memory. Per-pair
 * aggregator workers upsert candles here (one writer per pair/timeframe key, off the
 * matching hot path); HTTP threads read ranges concurrently. Candles are derived data
 * (rebuildable from the persisted trade log and the startup seed), so they live here
 * rather than in their own database table.
 *
 * <p>Thread-safe: each series is a {@link ConcurrentSkipListMap} ordered by open time,
 * so upserts and range reads never corrupt one another.
 */
public final class CandleStore {

  private final ConcurrentHashMap<String, ConcurrentNavigableMap<Instant, Candle>> series =
      new ConcurrentHashMap<>();

  private static String key(String pairId, Timeframe timeframe) {
    return pairId + "|" + timeframe.name();
  }

  /**
   * Inserts or replaces a candle (keyed by pair, timeframe, open time).
   *
   * @param candle the candle
   */
  public void upsert(Candle candle) {
    series
        .computeIfAbsent(
            key(candle.pairId(), candle.timeframe()), k -> new ConcurrentSkipListMap<>())
        .put(candle.openTime(), candle);
  }

  /**
   * The candles for a pair and timeframe whose open time is within {@code [from, to]},
   * in ascending time order.
   *
   * @param pairId    the pair
   * @param timeframe the timeframe
   * @param from      the inclusive lower bound
   * @param to        the inclusive upper bound
   * @return the candles in range (empty if none)
   */
  public List<Candle> query(String pairId, Timeframe timeframe, Instant from, Instant to) {
    ConcurrentNavigableMap<Instant, Candle> map = series.get(key(pairId, timeframe));
    if (map == null || from.isAfter(to)) {
      return List.of();
    }
    return new ArrayList<>(map.subMap(from, true, to, true).values());
  }

  /**
   * The open time of the earliest REAL (non-seeded) candle for a pair and timeframe -
   * the launch boundary the chart uses to delineate seeded pre-history from real data.
   *
   * @param pairId    the pair
   * @param timeframe the timeframe
   * @return the boundary open time, or empty if there are no real candles yet
   */
  public Optional<Instant> realBoundary(String pairId, Timeframe timeframe) {
    ConcurrentNavigableMap<Instant, Candle> map = series.get(key(pairId, timeframe));
    if (map == null) {
      return Optional.empty();
    }
    return map.values().stream()
        .filter(candle -> candle.source() == CandleSource.REAL)
        .map(Candle::openTime)
        .findFirst();
  }
}
