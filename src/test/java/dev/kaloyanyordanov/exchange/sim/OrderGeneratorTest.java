package dev.kaloyanyordanov.exchange.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.sim.OrderGenerator.GeneratedOrder;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OrderGeneratorTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 5L, 2L);

  private static SimulatorConfig config() {
    return config(0.5, 4L);
  }

  private static SimulatorConfig config(double aggression, long spreadTicks) {
    return new SimulatorConfig(
        1, 1, 0L, 1_000L, 100L, spreadTicks, 2L, 10L, List.of(1L), 42L, 1000, 0L, 0L, aggression);
  }

  @Test
  void generatesValidAlignedOrdersOnBothSidesWithinTheBand() {
    OrderGenerator generator = new OrderGenerator(SYMBOL, config(), new Random(1L));
    EnumSet<Side> sidesSeen = EnumSet.noneOf(Side.class);
    for (int i = 0; i < 500; i++) {
      GeneratedOrder order = generator.next();
      assertThat(SYMBOL.isValidPrice(order.price()))
          .as("price %d aligns to tick", order.price())
          .isTrue();
      assertThat(SYMBOL.isValidQuantity(order.quantity()))
          .as("quantity %d aligns to lot", order.quantity())
          .isTrue();
      // Band: mid +/- spread*tick = 100 +/- 20 -> [80, 120].
      assertThat(order.price()).isBetween(80L, 120L);
      assertThat(order.quantity()).isBetween(2L, 10L);
      sidesSeen.add(order.side());
    }
    assertThat(sidesSeen).containsExactlyInAnyOrder(Side.BUY, Side.SELL);
  }

  @Test
  void passiveFlowRestsOnTheCorrectSideFormingSpread() {
    // aggression 0: every order is a maker. Buys must rest below the mid (bids) and
    // sells above it (asks), leaving a real spread around mid (100).
    OrderGenerator generator = new OrderGenerator(SYMBOL, config(0.0, 4L), new Random(3L));
    for (int i = 0; i < 500; i++) {
      GeneratedOrder order = generator.next();
      if (order.side() == Side.BUY) {
        assertThat(order.price()).as("passive bid below mid").isLessThan(100L);
      } else {
        assertThat(order.price()).as("passive ask above mid").isGreaterThan(100L);
      }
    }
  }

  @Test
  void aggressiveFlowCrossesTheMid() {
    // aggression 1: every order is a taker. Buys sit at/above the mid and sells
    // at/below it, so they cross resting liquidity.
    OrderGenerator generator = new OrderGenerator(SYMBOL, config(1.0, 4L), new Random(5L));
    for (int i = 0; i < 500; i++) {
      GeneratedOrder order = generator.next();
      if (order.side() == Side.BUY) {
        assertThat(order.price()).as("aggressive buy at/above mid").isGreaterThanOrEqualTo(100L);
      } else {
        assertThat(order.price()).as("aggressive sell at/below mid").isLessThanOrEqualTo(100L);
      }
    }
  }

  @Test
  void zeroSpreadPlacesEveryOrderAtTheMid() {
    OrderGenerator generator = new OrderGenerator(SYMBOL, config(0.5, 0L), new Random(9L));
    for (int i = 0; i < 50; i++) {
      assertThat(generator.next().price()).isEqualTo(100L);
    }
  }

  @Test
  void sameSeedProducesTheSameStream() {
    OrderGenerator a = new OrderGenerator(SYMBOL, config(), new Random(7L));
    OrderGenerator b = new OrderGenerator(SYMBOL, config(), new Random(7L));
    for (int i = 0; i < 20; i++) {
      assertThat(a.next()).isEqualTo(b.next());
    }
  }
}
