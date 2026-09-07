package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.OrderId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A test event sink that records every published event. Thread-safe so it can be
 * used from the matching thread during concurrency tests; the accessors are
 * intended for use after the engine has stopped.
 */
final class RecordingEventPublisher implements EventPublisher {

  private final ConcurrentLinkedQueue<EngineEvent> events = new ConcurrentLinkedQueue<>();

  @Override
  public void publish(EngineEvent event) {
    events.add(event);
  }

  List<EngineEvent> events() {
    return new ArrayList<>(events);
  }

  List<OrderAccepted> accepted() {
    List<OrderAccepted> result = new ArrayList<>();
    for (EngineEvent event : events) {
      if (event instanceof OrderAccepted accepted) {
        result.add(accepted);
      }
    }
    return result;
  }

  List<TradeExecuted> trades() {
    List<TradeExecuted> result = new ArrayList<>();
    for (EngineEvent event : events) {
      if (event instanceof TradeExecuted trade) {
        result.add(trade);
      }
    }
    return result;
  }

  long acceptedSequenceOf(OrderId id) {
    for (EngineEvent event : events) {
      if (event instanceof OrderAccepted accepted && accepted.id().equals(id)) {
        return accepted.sequence();
      }
    }
    throw new IllegalStateException("no OrderAccepted for " + id);
  }
}
