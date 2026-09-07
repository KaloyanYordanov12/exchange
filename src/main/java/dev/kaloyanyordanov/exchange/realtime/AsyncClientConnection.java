package dev.kaloyanyordanov.exchange.realtime;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.jctools.queues.MpscArrayQueue;

/**
 * A per-client outbound connection that isolates a slow socket. {@link #deliver}
 * is a non-blocking enqueue onto a bounded buffer (drop-and-count on full, which
 * coalesces a slow client's backlog); a dedicated drain thread performs the
 * actual — possibly blocking — socket send. So a slow or stuck client only ever
 * affects its own buffer, never the flusher, other clients, or the engine.
 */
public final class AsyncClientConnection implements ClientSink {

  /** The blocking send to the underlying transport (e.g. a WebSocket session). */
  @FunctionalInterface
  public interface RawSender {
    /**
     * Sends one message, possibly blocking.
     *
     * @param message the message to send
     * @throws IOException if the send fails
     */
    void send(String message) throws IOException;
  }

  private final String id;
  private final RawSender sender;
  private final MpscArrayQueue<String> outbound;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong failures = new AtomicLong();
  private volatile boolean running;
  private Thread worker;

  /**
   * Creates a connection.
   *
   * @param id             an identifier for the client (thread naming/diagnostics)
   * @param bufferCapacity the outbound buffer capacity (rounded up to a power of two)
   * @param sender         the blocking transport send
   */
  public AsyncClientConnection(String id, int bufferCapacity, RawSender sender) {
    if (bufferCapacity <= 0) {
      throw new IllegalArgumentException("buffer capacity must be positive: " + bufferCapacity);
    }
    this.id = id;
    this.sender = sender;
    this.outbound = new MpscArrayQueue<>(bufferCapacity);
  }

  @Override
  public void deliver(String message) {
    if (!outbound.offer(message)) {
      dropped.incrementAndGet(); // Slow client: drop (coalesce) rather than block.
    }
  }

  /** Starts the drain thread. */
  public void start() {
    running = true;
    worker = new Thread(this::drain, "ws-client-" + id);
    worker.start();
  }

  private void drain() {
    while (running || !outbound.isEmpty()) {
      String message = outbound.poll();
      if (message == null) {
        Thread.onSpinWait();
        continue;
      }
      try {
        sender.send(message);
      } catch (Exception failure) {
        failures.incrementAndGet(); // A failed send affects only this client.
      }
    }
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
   * Messages dropped because the buffer was full.
   *
   * @return the drop count
   */
  public long droppedCount() {
    return dropped.get();
  }

  /**
   * Sends that threw.
   *
   * @return the failure count
   */
  public long failureCount() {
    return failures.get();
  }
}
