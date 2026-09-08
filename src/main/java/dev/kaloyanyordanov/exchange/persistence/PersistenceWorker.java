package dev.kaloyanyordanov.exchange.persistence;

import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.AsyncEventConsumer;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists one engine's order and trade audit stream to Postgres, asynchronously and
 * off the hot path. The matching thread only enqueues events into a bounded buffer
 * (a non-blocking {@link #publish}); a dedicated worker thread drains batches and
 * writes them in one transaction. A slow or paused database backs up only this
 * worker's buffer and never slows matching.
 *
 * <p>Cash movements (deposits, withdrawals) are audited separately by
 * {@link CashAuditWorker}, since cash is shared across engines and owned by the cash
 * ledger, not by any one engine.
 */
public final class PersistenceWorker implements EventPublisher {

  private final AsyncEventConsumer consumer;
  private final OrderRepository orderRepository;
  private final TradeRepository tradeRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * Creates the worker.
   *
   * @param orderRepository    the orders repository
   * @param tradeRepository    the trades repository
   * @param transactionManager the transaction manager
   * @param capacity           the bounded buffer capacity
   * @param maxBatch           the maximum events written per transaction
   */
  public PersistenceWorker(
      OrderRepository orderRepository,
      TradeRepository tradeRepository,
      PlatformTransactionManager transactionManager,
      int capacity,
      int maxBatch) {
    this.orderRepository = orderRepository;
    this.tradeRepository = tradeRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.consumer = new AsyncEventConsumer("persistence", capacity, maxBatch, this::persistBatch);
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
   * Stops the worker, draining outstanding events first.
   *
   * @throws InterruptedException if interrupted while stopping
   */
  public void stop() throws InterruptedException {
    consumer.stop();
  }

  void persistBatch(List<EngineEvent> batch) {
    List<OrderEntity> orders = new ArrayList<>();
    List<TradeEntity> trades = new ArrayList<>();
    for (EngineEvent event : batch) {
      switch (event) {
        case OrderAccepted accepted ->
            orders.add(
                new OrderEntity(
                    accepted.id().value(),
                    accepted.side(),
                    accepted.price(),
                    accepted.quantity(),
                    accepted.accountId(),
                    accepted.sequence()));
        case TradeExecuted executed -> trades.add(toTradeEntity(executed.trade()));
        default -> {
          // OrderRejected, BookChanged, and AccountUpdated are not part of this log.
        }
      }
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          if (!orders.isEmpty()) {
            orderRepository.saveAll(orders);
          }
          if (!trades.isEmpty()) {
            tradeRepository.saveAll(trades);
          }
        });
  }

  private static TradeEntity toTradeEntity(Trade trade) {
    return new TradeEntity(
        trade.sequence(),
        trade.buyOrderId().value(),
        trade.sellOrderId().value(),
        trade.price(),
        trade.quantity(),
        trade.buyerAccountId(),
        trade.sellerAccountId());
  }

  /**
   * The number of events buffered but not yet persisted.
   *
   * @return the buffered event count
   */
  public int pendingCount() {
    return consumer.queueSize();
  }

  /**
   * The number of batches that failed to persist.
   *
   * @return the failure count
   */
  public long failureCount() {
    return consumer.handlerFailures();
  }
}
