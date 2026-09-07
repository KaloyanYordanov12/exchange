package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.Trade;

/**
 * Emitted for each fill produced by the matching thread.
 *
 * @param trade the executed fill
 */
public record TradeExecuted(Trade trade) implements EngineEvent {

  /** Validates the event. */
  public TradeExecuted {
    if (trade == null) {
      throw new IllegalArgumentException("trade must be provided");
    }
  }
}
