package dev.kaloyanyordanov.exchange.sim;

import java.util.List;

/**
 * Configuration for one simulator run. Traders are spread round-robin over the
 * given (pre-funded) accounts and each submits up to {@code ordersPerTrader}
 * orders, paced at {@code orderRatePerSecond} (0 = as fast as possible), stopping
 * at {@code maxDurationMillis}. Prices are a balanced random walk within
 * {@code priceSpreadTicks} of {@code midPrice}, so the book actually trades.
 *
 * @param traderCount        number of concurrent (virtual-thread) traders
 * @param ordersPerTrader    max orders each trader submits
 * @param orderRatePerSecond per-trader pacing in orders/second (0 = unbounded)
 * @param maxDurationMillis  overall run time cap in milliseconds
 * @param midPrice           the mid price in ticks (a tick multiple)
 * @param priceSpreadTicks   max offset from mid, in ticks, for generated prices
 * @param minQuantity        minimum order quantity in units
 * @param maxQuantity        maximum order quantity in units
 * @param accountIds         the pre-funded accounts traders act on behalf of
 * @param randomSeed         seed for reproducible per-trader order streams
 * @param maxLatencySamples  cap on retained latency samples
 */
public record SimulatorConfig(
    int traderCount,
    int ordersPerTrader,
    long orderRatePerSecond,
    long maxDurationMillis,
    long midPrice,
    long priceSpreadTicks,
    long minQuantity,
    long maxQuantity,
    List<Long> accountIds,
    long randomSeed,
    int maxLatencySamples) {

  /** Validates the configuration. */
  public SimulatorConfig {
    if (traderCount <= 0) {
      throw new IllegalArgumentException("traderCount must be positive: " + traderCount);
    }
    if (ordersPerTrader <= 0) {
      throw new IllegalArgumentException("ordersPerTrader must be positive: " + ordersPerTrader);
    }
    if (orderRatePerSecond < 0) {
      throw new IllegalArgumentException("orderRatePerSecond must be non-negative");
    }
    if (maxDurationMillis <= 0) {
      throw new IllegalArgumentException("maxDurationMillis must be positive");
    }
    if (midPrice <= 0) {
      throw new IllegalArgumentException("midPrice must be positive: " + midPrice);
    }
    if (priceSpreadTicks < 0) {
      throw new IllegalArgumentException("priceSpreadTicks must be non-negative");
    }
    if (minQuantity <= 0 || maxQuantity < minQuantity) {
      throw new IllegalArgumentException("require 0 < minQuantity <= maxQuantity");
    }
    if (accountIds == null || accountIds.isEmpty()) {
      throw new IllegalArgumentException("at least one account id is required");
    }
    if (maxLatencySamples <= 0) {
      throw new IllegalArgumentException("maxLatencySamples must be positive");
    }
    accountIds = List.copyOf(accountIds);
  }
}
