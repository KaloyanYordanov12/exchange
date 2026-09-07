package dev.kaloyanyordanov.exchange.engine;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fans one event stream out to several sinks (read model, broadcaster,
 * persistence). Called on the matching thread, so every sink must be
 * non-blocking. A misbehaving sink is isolated: its exception is caught and
 * counted rather than propagated, so a faulty consumer can never take down the
 * matching thread or starve the other sinks.
 */
public final class FanoutPublisher implements EventPublisher {

  private final List<EventPublisher> sinks;
  private final AtomicLong sinkFailures = new AtomicLong();

  /**
   * Creates a fan-out over the given sinks.
   *
   * @param sinks the downstream sinks (defensively copied)
   */
  public FanoutPublisher(List<EventPublisher> sinks) {
    this.sinks = List.copyOf(sinks);
  }

  @Override
  public void publish(EngineEvent event) {
    for (EventPublisher sink : sinks) {
      try {
        sink.publish(event);
      } catch (RuntimeException failure) {
        // Isolate: one bad sink must not stop the others or the matching thread.
        sinkFailures.incrementAndGet();
      }
    }
  }

  /**
   * The number of sink exceptions swallowed so far (observability).
   *
   * @return the cumulative sink failure count
   */
  public long sinkFailures() {
    return sinkFailures.get();
  }
}
