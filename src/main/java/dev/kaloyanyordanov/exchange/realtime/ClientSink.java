package dev.kaloyanyordanov.exchange.realtime;

/**
 * A destination for broadcast messages. Delivery must be non-blocking: the
 * throttling flusher calls {@link #deliver(String)} for every client, so a slow
 * client must absorb (or drop) without stalling the flusher or other clients.
 */
public interface ClientSink {

  /**
   * Offers a message to this client. Must not block; a client that cannot keep
   * up drops or coalesces its own backlog.
   *
   * @param message the serialized message
   */
  void deliver(String message);
}
