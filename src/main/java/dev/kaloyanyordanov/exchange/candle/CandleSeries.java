package dev.kaloyanyordanov.exchange.candle;

import java.util.List;

/**
 * A pair's candle series for one timeframe and range, plus the boundary where real
 * (post-launch) candles begin, so the chart can mark seeded pre-history distinctly.
 *
 * @param pair         the pair id
 * @param timeframe    the timeframe label
 * @param realBoundary the epoch-second open time of the first REAL candle, or
 *     {@code null} if there are none yet
 * @param candles      the candles in ascending time order
 */
public record CandleSeries(
    String pair, String timeframe, Long realBoundary, List<CandlePoint> candles) {

  /** Defensive copy. */
  public CandleSeries {
    candles = List.copyOf(candles);
  }
}
