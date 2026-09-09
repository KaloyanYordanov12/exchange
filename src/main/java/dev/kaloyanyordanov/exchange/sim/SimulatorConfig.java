package dev.kaloyanyordanov.exchange.sim;

import java.util.List;

/**
 * Configuration for one simulator run. Traders are spread round-robin over the
 * given (pre-funded) accounts and each submits up to {@code ordersPerTrader} orders
 * ({@code 0} = unbounded), stopping at {@code maxDurationMillis} ({@code 0} = run
 * continuously until {@link LoadSimulator#stop()}). Prices are a balanced random walk
 * within {@code priceSpreadTicks} of {@code midPrice}, so the book actually trades.
 *
 * <p>Pacing: between orders each trader pauses a randomized human-like think-time,
 * uniformly in {@code [minThinkMillis, maxThinkMillis]}, so a large roster behaves
 * like a realistic market rather than a tight submit loop. Setting both think bounds
 * to zero gives the max-throughput stress-test mode. When no think-time is set,
 * {@code orderRatePerSecond} provides a fixed fallback pace (0 = as fast as possible).
 *
 * <p>Flow shape: {@code aggression} is the fraction of orders that cross the mid
 * (takers) rather than rest away from it (makers). A passive buy rests as a bid below
 * the mid and a passive sell as an ask above it, so a two-sided book with a real
 * spread forms; the aggressive minority crosses that spread and trades. {@code 0} is
 * an all-passive book, {@code 1} is all-crossing flow.
 *
 * @param traderCount        number of concurrent (virtual-thread) traders
 * @param ordersPerTrader    max orders each trader submits (0 = unbounded)
 * @param orderRatePerSecond fixed fallback per-trader pace in orders/second (0 =
 *     unbounded), used only when no think-time is set
 * @param maxDurationMillis  overall run time cap in milliseconds (0 = run until stopped)
 * @param midPrice           the mid price in ticks (a tick multiple)
 * @param priceSpreadTicks   max offset from mid, in ticks, for generated prices
 * @param minQuantity        minimum order quantity in units
 * @param maxQuantity        maximum order quantity in units
 * @param accountIds         the pre-funded accounts traders act on behalf of
 * @param randomSeed         seed for reproducible per-trader order streams
 * @param maxLatencySamples  cap on retained latency samples
 * @param minThinkMillis     lower bound of the randomized per-trader think-time
 * @param maxThinkMillis     upper bound of the randomized per-trader think-time
 *     (0 disables think-time; falls back to {@code orderRatePerSecond})
 * @param aggression         fraction (0..1) of orders that cross the mid (taker) vs
 *     rest away from it (maker); higher means more trades, lower a deeper book
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
    int maxLatencySamples,
    long minThinkMillis,
    long maxThinkMillis,
    double aggression) {

  /** Validates the configuration. */
  public SimulatorConfig {
    if (traderCount <= 0) {
      throw new IllegalArgumentException("traderCount must be positive: " + traderCount);
    }
    if (ordersPerTrader < 0) {
      throw new IllegalArgumentException(
          "ordersPerTrader must be non-negative (0 = unbounded): " + ordersPerTrader);
    }
    if (orderRatePerSecond < 0) {
      throw new IllegalArgumentException("orderRatePerSecond must be non-negative");
    }
    if (maxDurationMillis < 0) {
      throw new IllegalArgumentException(
          "maxDurationMillis must be non-negative (0 = run until stopped)");
    }
    if (minThinkMillis < 0 || maxThinkMillis < 0) {
      throw new IllegalArgumentException("think-time bounds must be non-negative");
    }
    if (maxThinkMillis > 0 && minThinkMillis > maxThinkMillis) {
      throw new IllegalArgumentException("require minThinkMillis <= maxThinkMillis");
    }
    if (aggression < 0.0 || aggression > 1.0) {
      throw new IllegalArgumentException("aggression must be in [0, 1]: " + aggression);
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
