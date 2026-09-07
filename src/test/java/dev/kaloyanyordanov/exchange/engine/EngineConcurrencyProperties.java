package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderBook;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Concurrency property: random concurrent submission still yields a book that is
 * non-crossed and identical to the deterministic single-threaded replay of the
 * exact sequence the engine processed. The single-threaded core plus determinism
 * is what makes a concurrent outcome provable.
 */
class EngineConcurrencyProperties {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  /** A generated order intent (id assigned from the list index at submission). */
  record Spec(Side side, long price, long quantity, long account) {}

  @Provide
  Arbitrary<List<Spec>> flows() {
    Arbitrary<Side> sides = Arbitraries.of(Side.BUY, Side.SELL);
    Arbitrary<Long> prices = Arbitraries.longs().between(1L, 20L);
    Arbitrary<Long> quantities = Arbitraries.longs().between(1L, 10L);
    Arbitrary<Long> accounts = Arbitraries.longs().between(0L, 5L);
    Arbitrary<Spec> spec = Combinators.combine(sides, prices, quantities, accounts).as(Spec::new);
    return spec.list().ofMinSize(0).ofMaxSize(40);
  }

  @Property(tries = 25)
  void concurrentSubmissionMatchesDeterministicReplay(@ForAll("flows") List<Spec> specs)
      throws InterruptedException {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1 << 12, publisher);

    Map<OrderId, SubmitOrder> commands = new HashMap<>();
    List<Thread> producers = new ArrayList<>(specs.size());
    for (int i = 0; i < specs.size(); i++) {
      Spec spec = specs.get(i);
      OrderId id = OrderId.of(i);
      SubmitOrder command =
          new SubmitOrder(id, spec.side(), spec.price(), spec.quantity(), spec.account());
      commands.put(id, command);
      producers.add(
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    while (engine.submit(command) != SubmitResult.ENQUEUED) {
                      Thread.onSpinWait();
                    }
                  }));
    }

    engine.start();
    producers.forEach(Thread::start);
    for (Thread producer : producers) {
      producer.join();
    }
    engine.stop();

    List<OrderAccepted> accepted = publisher.accepted();
    assertThat(accepted).hasSize(specs.size());

    OrderBook replay = EngineReplaySupport.replay(SYMBOL, accepted, commands);
    assertThat(EngineReplaySupport.sortedResting(engine.restingOrders()))
        .isEqualTo(EngineReplaySupport.sortedResting(replay.restingOrders()));
    assertThat(engine.snapshot()).isEqualTo(replay.snapshot());
    assertThat(engine.isCrossed()).isFalse();
  }
}
