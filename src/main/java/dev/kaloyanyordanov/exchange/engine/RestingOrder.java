package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.Side;

/**
 * A raw resting order in a snapshot. Deliberately unvalidated (unlike the domain
 * {@code Order}) so the invariant checker can be tested against deliberately
 * corrupted snapshots — e.g. an overfilled order ({@code remaining > quantity}).
 *
 * @param orderId  the order id
 * @param side     the side
 * @param price    the price in ticks
 * @param quantity the original quantity
 * @param remaining the remaining (unfilled) quantity
 * @param sequence the arrival sequence (time priority)
 */
public record RestingOrder(
    long orderId, Side side, long price, long quantity, long remaining, long sequence) {}
