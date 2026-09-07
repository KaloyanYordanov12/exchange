package dev.kaloyanyordanov.exchange.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jctools.queues.MpscArrayQueue;

/**
 * An outbound sink that hands events to a batch handler on its own thread. The
 * matching thread's {@link #publish} is a non-blocking enqueue onto a bounded
 * buffer (drop-and-count on full); a dedicated drain thread pulls batches and
 * invokes the handler. So a slow handler (e.g. a slow database) only backs up
 * this consumer's own buffer and never blocks the matching thread — this is how
 * persistence stays off the hot path.
 */
public final class AsyncEventConsumer implements EventPublisher {

  /** Handles a batch of events (e.g. a batched database write). */
  @FunctionalInterface
  public interface BatchHandler {
    /**
     * Handles one batch.
     *
     * @param batch the events to handle, in order
     */
    void handle(List<EngineEvent> batch);
  }

  private final String name;
  private final MpscArrayQueue<EngineEvent> queue;
  private final BatchHandler handler;
  private final int maxBatch;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong handlerFailures = new AtomicLong();
  private volatile boolean running;
  private Thread worker;

  /**
   * Creates a consumer.
   *
   * @param name     a name for the drain thread
   * @param capacity the bounded buffer capacity (rounded up to a power of two)
   * @param maxBatch the maximum events handed to the handler at once
   * @param handler  the batch handler
   */
  public AsyncEventConsumer(String name, int capacity, int maxBatch, BatchHandler handler) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive: " + capacity);
    }
    if (maxBatch <= 0) {
      throw new IllegalArgumentException("max batch must be positive: " + maxBatch);
    }
    this.name = name;
    this.queue = new MpscArrayQueue<>(capacity);
    this.maxBatch = maxBatch;
    this.handler = handler;
  }

  @Override
  public void publish(EngineEvent event) {
    if (!queue.offer(event)) {
      dropped.incrementAndGet(); // Buffer full: drop rather than block the matching thread.
    }
  }

  /** Starts the drain thread. */
  public void start() {
    running = true;
    worker = new Thread(this::drain, name);
    worker.start();
  }

  private void drain() {
    while (running || !queue.isEmpty()) {
      List<EngineEvent> batch = nextBatch();
      if (batch.isEmpty()) {
        Thread.onSpinWait();
        continue;
      }
      try {
        handler.handle(batch);
      } catch (RuntimeException failure) {
        handlerFailures.incrementAndGet(); // A failed write must not kill the worker.
      }
    }
  }

  private List<EngineEvent> nextBatch() {
    List<EngineEvent> batch = new ArrayList<>();
    EngineEvent event;
    while (batch.size() < maxBatch && (event = queue.poll()) != null) {
      batch.add(event);
    }
    return batch;
  }

  /**
   * Stops draining and joins the drain thread.
   *
   * @throws InterruptedException if interrupted while joining
   */
  public void stop() throws InterruptedException {
    running = false;
    if (worker != null) {
      worker.join();
    }
  }

  /**
   * The number of events currently buffered (not yet handled).
   *
   * @return the buffered event count
   */
  public int queueSize() {
    return queue.size();
  }

  /**
   * Events dropped because the buffer was full.
   *
   * @return the drop count
   */
  public long droppedCount() {
    return dropped.get();
  }

  /**
   * Batches that threw from the handler.
   *
   * @return the handler failure count
   */
  public long handlerFailures() {
    return handlerFailures.get();
  }
}
