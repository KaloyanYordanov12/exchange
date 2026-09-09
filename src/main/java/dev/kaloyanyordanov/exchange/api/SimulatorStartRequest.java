package dev.kaloyanyordanov.exchange.api;

/**
 * Request body to start a simulator run. Traders act on the configured (funded)
 * accounts; the caller only dials the load and price behaviour.
 *
 * @param pair               the pair id to run the simulator on ({@code base-quote})
 * @param traderCount        number of concurrent traders
 * @param ordersPerTrader    max orders each trader submits
 * @param orderRatePerSecond per-trader pacing (0 = unbounded)
 * @param durationMillis     overall run time cap in milliseconds
 * @param midPrice           the mid price in ticks
 * @param priceSpreadTicks   max offset from mid, in ticks
 * @param minQuantity        minimum order quantity in units
 * @param maxQuantity        maximum order quantity in units
 * @param randomSeed         seed for reproducible order streams
 * @param maxLatencySamples  cap on retained latency samples
 * @param minThinkMillis     lower bound of the randomized per-trader think-time
 * @param maxThinkMillis     upper bound of the randomized per-trader think-time
 *     (0 disables think-time and uses {@code orderRatePerSecond})
 */
public record SimulatorStartRequest(
    String pair,
    int traderCount,
    int ordersPerTrader,
    long orderRatePerSecond,
    long durationMillis,
    long midPrice,
    long priceSpreadTicks,
    long minQuantity,
    long maxQuantity,
    long randomSeed,
    int maxLatencySamples,
    long minThinkMillis,
    long maxThinkMillis) {}
