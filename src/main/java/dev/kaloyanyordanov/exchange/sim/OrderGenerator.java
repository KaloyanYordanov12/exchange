package dev.kaloyanyordanov.exchange.sim;

import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.random.RandomGenerator;

/**
 * Generates a balanced random walk of tick/lot-aligned orders around a mid price:
 * buys priced up to the mid and sells down to it, so the book crosses and trades
 * rather than only resting. Deterministic given the seed, and unit-testable.
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
    Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
    long spread = config.priceSpreadTicks();
    long offsetTicks = spread == 0 ? 0 : random.nextLong(spread + 1);
    long delta = offsetTicks * symbol.tickSize();
    // Buyers bid up from mid, sellers offer down from mid, so orders cross.
    long price = side == Side.BUY ? config.midPrice() + delta : config.midPrice() - delta;
    if (price < symbol.tickSize()) {
      price = symbol.tickSize();
    }
    return new GeneratedOrder(side, price, quantity());
  }

  private long quantity() {
    long range = config.maxQuantity() - config.minQuantity();
    long raw = config.minQuantity() + (range == 0 ? 0 : random.nextLong(range + 1));
    long lot = symbol.lotSize();
    long aligned = raw - (raw % lot);
    return aligned < lot ? lot : aligned;
  }
}
