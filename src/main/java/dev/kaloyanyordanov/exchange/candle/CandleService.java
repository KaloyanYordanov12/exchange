package dev.kaloyanyordanov.exchange.candle;

import java.time.Instant;
import java.util.List;

/**
 * Read facade over the {@link CandleStore} for the chart API: fetch a pair's candles
 * for a timeframe and time range, with the SEEDED/REAL boundary so the chart can
 * delineate synthetic pre-history from real trades.
 */
public final class CandleService {

  private final CandleStore store;

  /**
   * Creates the service.
   *
   * @param store the candle store
   */
  public CandleService(CandleStore store) {
    this.store = store;
  }

  /**
   * A pair's candle series for a timeframe and range.
   *
   * @param pairId    the pair id
   * @param timeframe the timeframe
   * @param from      the inclusive lower bound
   * @param to        the inclusive upper bound
   * @return the series with its real-data boundary
   */
  public CandleSeries series(String pairId, Timeframe timeframe, Instant from, Instant to) {
    List<CandlePoint> points =
        store.query(pairId, timeframe, from, to).stream().map(CandleService::toPoint).toList();
    Long boundary =
        store.realBoundary(pairId, timeframe).map(Instant::getEpochSecond).orElse(null);
    return new CandleSeries(pairId, timeframe.label(), boundary, points);
  }

  private static CandlePoint toPoint(Candle candle) {
    return new CandlePoint(
        candle.openTime().getEpochSecond(),
        candle.open(),
        candle.high(),
        candle.low(),
        candle.close(),
        candle.volume(),
        candle.source().name());
  }
}
