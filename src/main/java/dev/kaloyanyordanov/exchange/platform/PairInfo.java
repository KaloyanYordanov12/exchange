package dev.kaloyanyordanov.exchange.platform;

/**
 * Static descriptor of a tradable pair, for the markets/pairs listing. Prices are
 * scaled integers.
 *
 * @param pairId         the routing id ({@code base-quote})
 * @param base           the base asset
 * @param quote          the quote asset
 * @param tickSize       the minimum price increment in ticks
 * @param lotSize        the minimum quantity increment in units
 * @param referencePrice the reference/seed price in ticks
 */
public record PairInfo(
    String pairId, String base, String quote, long tickSize, long lotSize, long referencePrice) {}
