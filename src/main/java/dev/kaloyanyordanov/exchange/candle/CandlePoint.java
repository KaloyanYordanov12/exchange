package dev.kaloyanyordanov.exchange.candle;

/**
 * One candle in a chart series. Time is an epoch second; prices and volume are scaled
 * integers; source is {@code SEEDED} or {@code REAL}.
 *
 * @param time   the bucket open time, epoch seconds
 * @param open   the open price in ticks
 * @param high   the high price in ticks
 * @param low    the low price in ticks
 * @param close  the close price in ticks
 * @param volume the summed volume in units
 * @param source {@code SEEDED} or {@code REAL}
 */
public record CandlePoint(
    long time, long open, long high, long low, long close, long volume, String source) {}
