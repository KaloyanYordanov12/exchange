package dev.kaloyanyordanov.exchange.candle;

import java.time.Instant;

/**
 * An immutable OHLCV candle for one pair, timeframe, and time bucket. Prices and
 * volume are scaled integers. Deliberately unvalidated (like a raw snapshot record)
 * so the invariant checker and property tests can be exercised against deliberately
 * corrupted candles, not only valid ones.
 *
 * @param pairId    the pair id
 * @param timeframe the timeframe
 * @param openTime  the bucket start instant
 * @param open      the first trade price in the bucket
 * @param high      the highest trade price in the bucket
 * @param low       the lowest trade price in the bucket
 * @param close     the last trade price in the bucket
 * @param volume    the summed trade quantity in the bucket
 * @param source    whether this candle is REAL or a labeled SEEDED pre-history candle
 */
public record Candle(
    String pairId,
    Timeframe timeframe,
    Instant openTime,
    long open,
    long high,
    long low,
    long close,
    long volume,
    CandleSource source) {}
