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
    return new SimulatorConfig(1, 1, 0L, 1_000L, 100L, 4L, 2L, 10L, List.of(1L), 42L, 1000);
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
  void sameSeedProducesTheSameStream() {
    OrderGenerator a = new OrderGenerator(SYMBOL, config(), new Random(7L));
    OrderGenerator b = new OrderGenerator(SYMBOL, config(), new Random(7L));
    for (int i = 0; i < 20; i++) {
      assertThat(a.next()).isEqualTo(b.next());
    }
  }
}
