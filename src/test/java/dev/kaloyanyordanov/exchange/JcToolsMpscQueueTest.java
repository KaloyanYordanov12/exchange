package dev.kaloyanyordanov.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import org.jctools.queues.MpscArrayQueue;
import org.junit.jupiter.api.Test;

/**
 * Proves the JCTools dependency resolves and a {@link MpscArrayQueue} offers and
 * polls in FIFO order. No engine yet — this only shows the tool works, ahead of
 * its use as the ingress ring buffer.
 */
class JcToolsMpscQueueTest {

  @Test
  void offersAndPollsInFifoOrder() {
    MpscArrayQueue<Long> queue = new MpscArrayQueue<>(16);

    assertThat(queue.offer(1L)).isTrue();
    assertThat(queue.offer(2L)).isTrue();
    assertThat(queue.offer(3L)).isTrue();

    assertThat(queue.poll()).isEqualTo(1L);
    assertThat(queue.poll()).isEqualTo(2L);
    assertThat(queue.poll()).isEqualTo(3L);
    assertThat(queue.poll()).isNull();
  }
}
