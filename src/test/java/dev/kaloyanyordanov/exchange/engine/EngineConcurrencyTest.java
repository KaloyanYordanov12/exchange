package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderBook;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class EngineConcurrencyTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  @Test
  void manyProducersProcessedExactlyOnceAndMatchDeterministicReplay() throws InterruptedException {
    int orders = 12_000;
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1 << 16, publisher);

    Map<OrderId, SubmitOrder> commands = new HashMap<>();
    List<Thread> producers = new ArrayList<>(orders);
    for (int i = 0; i < orders; i++) {
      OrderId id = OrderId.of(i);
      Side side = (i % 2 == 0) ? Side.BUY : Side.SELL;
      SubmitOrder command = new SubmitOrder(id, side, 90L + (i % 20), 1L + (i % 5), i % 10);
      commands.put(id, command);
      producers.add(
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    // Retry on back-pressure so every order is eventually accepted.
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
    // Exactly once: one acceptance per order, ids unique, sequences a dense 0..n-1.
    assertThat(accepted).hasSize(orders);
    assertThat(accepted).extracting(OrderAccepted::id).doesNotHaveDuplicates();
    assertThat(accepted).extracting(OrderAccepted::id).containsAll(commands.keySet());
    assertThat(accepted.stream().mapToLong(OrderAccepted::sequence).sorted().toArray())
        .containsExactly(LongStream.range(0, orders).toArray());

    // Final book equals the deterministic single-threaded replay of that sequence.
    OrderBook replay = EngineReplaySupport.replay(SYMBOL, accepted, commands);
    assertThat(EngineReplaySupport.sortedResting(engine.restingOrders()))
        .isEqualTo(EngineReplaySupport.sortedResting(replay.restingOrders()));
    assertThat(engine.snapshot()).isEqualTo(replay.snapshot());
    assertThat(engine.isCrossed()).isFalse();
  }

  @Test
  void fullQueueRejectsWithBusyWithoutBlockingOrLosingCommands() throws InterruptedException {
    GatedPublisher publisher = new GatedPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 8, publisher);
    int capacity = engine.ingressCapacity();
    int attempts = capacity + 30;
    engine.start();

    List<SubmitResult> results = new ArrayList<>(attempts);
    // Submitting must never block, even though the consumer is stalled in publish.
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          for (int i = 0; i < attempts; i++) {
            results.add(
                engine.submit(new SubmitOrder(OrderId.of(i), Side.BUY, 100L, 1L, 0L)));
          }
        });

    long enqueued = results.stream().filter(r -> r == SubmitResult.ENQUEUED).count();
    long busy = results.stream().filter(r -> r == SubmitResult.REJECTED_BUSY).count();

    // The bounded queue never holds more than its capacity (plus at most the one
    // command the stalled consumer already polled), and back-pressure fired.
    assertThat(enqueued).isBetween((long) capacity, (long) capacity + 1);
    assertThat(busy).isEqualTo(attempts - enqueued).isPositive();

    // Release the consumer and drain: no enqueued command is lost.
    publisher.release();
    engine.stop();
    assertThat(publisher.accepted()).hasSize((int) enqueued);
  }

  /** A publisher whose first calls block until released, stalling the consumer. */
  private static final class GatedPublisher implements EventPublisher {
    private final CountDownLatch gate = new CountDownLatch(1);
    private final ConcurrentLinkedQueue<EngineEvent> events = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(EngineEvent event) {
      try {
        gate.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while gated", e);
      }
      events.add(event);
    }

    void release() {
      gate.countDown();
    }

    List<OrderAccepted> accepted() {
      List<OrderAccepted> result = new ArrayList<>();
      for (EngineEvent event : events) {
        if (event instanceof OrderAccepted a) {
          result.add(a);
        }
      }
      return result;
    }
  }
}
