package dev.kaloyanyordanov.exchange.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.AsyncEventConsumer;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Proves persistence is off the hot path: with the async consumer's handler stuck
 * (a stalled database), the matching engine still processes every order promptly
 * while events queue up for the consumer — matching throughput is independent of
 * the (blocked) persistence sink.
 */
class PersistenceOffHotPathTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  @Test
  void matchingContinuesWhileTheConsumerIsBlocked() throws InterruptedException {
    CountDownLatch handlerReached = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AsyncEventConsumer consumer =
        new AsyncEventConsumer(
            "blocked-db",
            1 << 16,
            500,
            batch -> {
              handlerReached.countDown();
              awaitQuietly(release);
            });
    consumer.start();

    MatchingEngine engine = new MatchingEngine(SYMBOL, 1 << 16, consumer);
    engine.start();

    int orders = 4000;
    // Matching must complete promptly even though the consumer is stuck on its
    // first batch — if persistence were on the hot path, this would hang.
    assertTimeoutPreemptively(
        Duration.ofSeconds(20),
        () -> {
          for (int i = 0; i < orders; i++) {
            SubmitOrder order = new SubmitOrder(OrderId.of(i), Side.BUY, 100L - (i % 50), 1L, 1L);
            while (engine.submit(order) != SubmitResult.ENQUEUED) {
              Thread.onSpinWait();
            }
          }
          engine.stop();
        });

    assertThat(handlerReached.await(5, TimeUnit.SECONDS))
        .as("the consumer reached its (blocking) handler")
        .isTrue();
    assertThat(engine.restingOrders()).as("matching processed every order").hasSize(orders);
    assertThat(consumer.queueSize())
        .as("events queued for the blocked consumer")
        .isPositive();

    release.countDown();
    consumer.stop();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
