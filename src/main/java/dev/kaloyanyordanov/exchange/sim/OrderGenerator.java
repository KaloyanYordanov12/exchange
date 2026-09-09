package dev.kaloyanyordanov.exchange.sim;

import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.random.RandomGenerator;

/**
 * Generates tick/lot-aligned orders around a mid price as a realistic mix of makers
 * and takers. Most orders are passive and rest on the correct side (buys as bids below
 * the mid, sells as asks above it), so a genuine two-sided book with a spread forms; a
 * minority (set by {@code aggression}) cross the mid and trade. Deterministic given the
 * seed, and unit-testable.
 */
final class OrderGenerator {

  /** A generated order intent (id and account are assigned by the caller). */
  record GeneratedOrder(Side side, long price, long quantity) {}

  private final Symbol symbol;
  private final SimulatorConfig config;
  private final RandomGenerator random;

  OrderGenerator(Symbol symbol, SimulatorConfig config, RandomGenerator random) {
    this.symbol = symbol;
    this.config = config;
    this.random = random;
  }

  GeneratedOrder next() {
    boolean buy = random.nextBoolean();
    boolean aggressive = random.nextDouble() < config.aggression();
    long maxOffset = config.priceSpreadTicks();
    // Passive orders keep at least a 1-tick gap from the mid so a real spread forms;
    // aggressive orders may sit right at the mid to cross.
    long minOffset = aggressive ? 0L : Math.min(1L, maxOffset);
    long span = maxOffset - minOffset;
    long offsetTicks = span <= 0L ? minOffset : minOffset + random.nextLong(span + 1);
    long delta = offsetTicks * symbol.tickSize();
    // Aggressive: buy above / sell below the mid, so it crosses and trades.
    // Passive: buy below (a resting bid) / sell above (a resting ask), building the book.
    long price =
        aggressive
            ? (buy ? config.midPrice() + delta : config.midPrice() - delta)
            : (buy ? config.midPrice() - delta : config.midPrice() + delta);
    if (price < symbol.tickSize()) {
      price = symbol.tickSize();
    }
    return new GeneratedOrder(buy ? Side.BUY : Side.SELL, price, quantity());
  }

  private long quantity() {
    long range = config.maxQuantity() - config.minQuantity();
    long raw = config.minQuantity() + (range == 0 ? 0 : random.nextLong(range + 1));
    long lot = symbol.lotSize();
    long aligned = raw - (raw % lot);
    return aligned < lot ? lot : aligned;
  }
}
