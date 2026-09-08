package dev.kaloyanyordanov.exchange.candle;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Aggregates one pair's trades into OHLCV candles for every {@link Timeframe}. It is
 * pure single-threaded logic: an async consumer feeds it trades (off the matching hot
 * path) in time order, and it emits the updated candle for each affected timeframe to
 * a sink, which upserts by (pair, timeframe, open time). Each timeframe is built
 * directly from the trades, which is exactly equal to rolling up the 1m base candles
 * over the window (proven by the candle invariants property test).
 */
public final class PairCandleAggregator {

  /** The candle currently being built for a timeframe. */
  private static final class Building {
    private Instant openTime;
    private long open;
    private long high;
    private long low;
    private long close;
    private long volume;
  }

  private final String pairId;
  private final CandleSource source;
  private final Consumer<Candle> sink;
  private final EnumMap<Timeframe, Building> building = new EnumMap<>(Timeframe.class);

  /**
   * Creates an aggregator.
   *
   * @param pairId the pair id
   * @param source the source label for emitted candles (REAL for live trades)
   * @param sink   receives the updated candle for each affected timeframe on each trade
   */
  public PairCandleAggregator(String pairId, CandleSource source, Consumer<Candle> sink) {
    this.pairId = Objects.requireNonNull(pairId, "pairId");
    this.source = Objects.requireNonNull(source, "source");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  /**
   * Folds a trade into every timeframe's current candle and emits each updated candle.
   * Trades must arrive in non-decreasing time order (they do: the consumer stamps them
   * with a monotonic wall clock).
   *
   * @param time     the trade time
   * @param price    the trade price in ticks
   * @param quantity the trade quantity in units
   */
  public void onTrade(Instant time, long price, long quantity) {
    for (Timeframe timeframe : Timeframe.values()) {
      Instant bucket = timeframe.bucketStart(time);
      Building current = building.get(timeframe);
      if (current == null || bucket.isAfter(current.openTime)) {
        current = new Building();
        current.openTime = bucket;
        current.open = price;
        current.high = price;
        current.low = price;
        current.close = price;
        current.volume = quantity;
        building.put(timeframe, current);
      } else if (bucket.equals(current.openTime)) {
        current.high = Math.max(current.high, price);
        current.low = Math.min(current.low, price);
        current.close = price;
        current.volume = Math.addExact(current.volume, quantity);
      } else {
        continue; // an out-of-order earlier bucket; ignore rather than corrupt a candle
      }
      sink.accept(
          new Candle(
              pairId,
              timeframe,
              current.openTime,
              current.open,
              current.high,
              current.low,
              current.close,
              current.volume,
              source));
    }
  }
}
