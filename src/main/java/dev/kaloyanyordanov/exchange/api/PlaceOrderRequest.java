package dev.kaloyanyordanov.exchange.api;

/**
 * Request body for placing a limit order on a pair.
 *
 * @param pair     the pair id ({@code base-quote}, e.g. {@code BTC-USD})
 * @param side     {@code BUY} or {@code SELL} (case-insensitive)
 * @param price    the limit price in scaled integer ticks
 * @param quantity the quantity in scaled integer units
 */
public record PlaceOrderRequest(String pair, String side, long price, long quantity) {}
