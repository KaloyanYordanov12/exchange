package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AsyncClientConnectionTest {

  @Test
  void rejectsNonPositiveCapacity() {
    assertThatThrownBy(() -> new AsyncClientConnection("c", 0, message -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deliversQueuedMessagesToTheSender() throws InterruptedException {
    ConcurrentLinkedQueue<String> sent = new ConcurrentLinkedQueue<>();
    AsyncClientConnection connection = new AsyncClientConnection("c", 64, sent::add);
    connection.start();
    connection.deliver("a");
    connection.deliver("b");
    await().atMost(Duration.ofSeconds(2)).until(() -> sent.size() == 2);
    connection.stop();
    assertThat(sent).containsExactly("a", "b");
    assertThat(connection.droppedCount()).isZero();
  }

  @Test
  void dropsWhenBufferIsFullWithoutBlocking() throws InterruptedException {
    ConcurrentLinkedQueue<String> sent = new ConcurrentLinkedQueue<>();
    // Not started: nothing drains, so the bounded buffer fills and excess drops.
    AsyncClientConnection connection = new AsyncClientConnection("c", 8, sent::add);
    for (int i = 0; i < 100; i++) {
      connection.deliver("m" + i);
    }
    assertThat(connection.droppedCount()).isPositive();

    connection.start();
    await()
        .atMost(Duration.ofSeconds(2))
        .until(() -> sent.size() + connection.droppedCount() == 100);
    connection.stop();
    // Every message is accounted for: delivered or dropped, none lost silently.
    assertThat(sent.size() + connection.droppedCount()).isEqualTo(100L);
  }

  @Test
  void failingSendAffectsOnlyThisClient() throws InterruptedException {
    AtomicLong attempts = new AtomicLong();
    AsyncClientConnection connection =
        new AsyncClientConnection(
            "c",
            64,
            message -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("socket closed");
            });
    connection.start();
    connection.deliver("a");
    connection.deliver("b");
    await().atMost(Duration.ofSeconds(2)).until(() -> connection.failureCount() == 2);
    connection.stop();
    assertThat(attempts.get()).isEqualTo(2L);
    assertThat(connection.failureCount()).isEqualTo(2L);
  }
}
