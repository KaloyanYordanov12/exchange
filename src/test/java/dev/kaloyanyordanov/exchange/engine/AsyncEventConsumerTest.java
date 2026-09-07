package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AsyncEventConsumerTest {

  private static OrderAccepted event(long id) {
    return new OrderAccepted(OrderId.of(id), Side.BUY, 100L, 1L, 1L, id);
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThatThrownBy(() -> new AsyncEventConsumer("c", 0, 4, batch -> {}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AsyncEventConsumer("c", 16, 0, batch -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deliversEventsToTheHandlerInBatches() throws InterruptedException {
    ConcurrentLinkedQueue<EngineEvent> handled = new ConcurrentLinkedQueue<>();
    AtomicLong batches = new AtomicLong();
    AsyncEventConsumer consumer =
        new AsyncEventConsumer(
            "c",
            1024,
            4,
            batch -> {
              batches.incrementAndGet();
              handled.addAll(batch);
            });
    consumer.start();
    for (int i = 0; i < 20; i++) {
      consumer.publish(event(i));
    }
    await().atMost(Duration.ofSeconds(2)).until(() -> handled.size() == 20);
    consumer.stop();
    assertThat(handled).hasSize(20);
    assertThat(batches.get()).isPositive();
  }

  @Test
  void dropsWhenBufferFullWithoutBlocking() {
    // Not started: nothing drains, so the bounded buffer fills and excess drops.
    AsyncEventConsumer consumer = new AsyncEventConsumer("c", 8, 4, batch -> {});
    for (int i = 0; i < 100; i++) {
      consumer.publish(event(i));
    }
    assertThat(consumer.droppedCount()).isPositive();
    assertThat(consumer.queueSize() + consumer.droppedCount()).isEqualTo(100L);
  }

  @Test
  void failingHandlerDoesNotKillTheWorker() throws InterruptedException {
    CountDownLatch firstHandled = new CountDownLatch(1);
    AsyncEventConsumer consumer =
        new AsyncEventConsumer(
            "c",
            64,
            1,
            batch -> {
              firstHandled.countDown();
              throw new IllegalStateException("db down");
            });
    consumer.start();
    consumer.publish(event(1));
    consumer.publish(event(2));
    await().atMost(Duration.ofSeconds(2)).until(() -> consumer.handlerFailures() >= 2);
    consumer.stop();
    assertThat(consumer.handlerFailures()).isGreaterThanOrEqualTo(2L);
  }

  @Test
  void queueSizeReflectsBufferedEvents() {
    AsyncEventConsumer consumer = new AsyncEventConsumer("c", 64, 4, batch -> {});
    consumer.publish(event(1));
    consumer.publish(event(2));
    assertThat(consumer.queueSize()).isEqualTo(2);
  }

  @Test
  void handlerReceivesEventsInOrder() throws InterruptedException {
    ConcurrentLinkedQueue<EngineEvent> handled = new ConcurrentLinkedQueue<>();
    AsyncEventConsumer consumer =
        new AsyncEventConsumer("c", 64, 8, handled::addAll);
    consumer.start();
    List<OrderAccepted> sent = List.of(event(0), event(1), event(2));
    sent.forEach(consumer::publish);
    await().atMost(Duration.ofSeconds(2)).until(() -> handled.size() == 3);
    consumer.stop();
    assertThat(handled).containsExactlyElementsOf(sent);
  }
}
