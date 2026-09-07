package dev.kaloyanyordanov.exchange.engine;

/**
 * Sink for outbound engine events. Called only on the matching thread, so
 * implementations must be cheap and non-blocking — the engine must never block
 * on a downstream consumer. Real throttled/batched egress is wired in later
 * phases; this run uses a simple recording consumer in tests.
 */
@FunctionalInterface
public interface EventPublisher {

  /**
   * Publishes one event. Must not block the matching thread.
   *
   * @param event the event to publish
   */
  void publish(EngineEvent event);
}
