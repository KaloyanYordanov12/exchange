package dev.kaloyanyordanov.exchange.candle;

import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.AsyncEventConsumer;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.time.Instant;
import java.util.List;

/**
 * Consumes one pair's trade stream off the matching hot path and folds each trade into
 * that pair's OHLCV candles. Like the persistence worker, the matching thread only
 * enqueues into a bounded buffer (a non-blocking {@link #publish}); a dedicated thread
 * drains batches and aggregates. A trade carries no clock (events are deterministic),
 * so this worker stamps each drained batch with a monotonic wall clock - the candle's
 * trade time. It never blocks matching.
 */
public final class CandleAggregatorWorker implements EventPublisher {

  private final AsyncEventConsumer consumer;
  private final PairCandleAggregator aggregator;

  /**
   * Creates the worker.
   *
   * @param pairId   the pair id
   * @param store    the shared candle store to upsert into
   * @param capacity the bounded buffer capacity
   * @param maxBatch the maximum trades folded per batch
   */
  public CandleAggregatorWorker(String pairId, CandleStore store, int capacity, int maxBatch) {
    this.aggregator = new PairCandleAggregator(pairId, CandleSource.REAL, store::upsert);
    this.consumer =
        new AsyncEventConsumer("candles-" + pairId, capacity, maxBatch, this::aggregateBatch);
  }

  @Override
  public void publish(EngineEvent event) {
    consumer.publish(event);
  }

  /** Starts the worker's drain thread. */
  public void start() {
    consumer.start();
  }

  /**
   * Stops the worker, draining outstanding trades first.
   *
   * @throws InterruptedException if interrupted while stopping
   */
  public void stop() throws InterruptedException {
    consumer.stop();
  }

  void aggregateBatch(List<EngineEvent> batch) {
    Instant now = Instant.now();
    for (EngineEvent event : batch) {
      if (event instanceof TradeExecuted executed) {
        Trade trade = executed.trade();
        aggregator.onTrade(now, trade.price(), trade.quantity());
      }
    }
  }
}
