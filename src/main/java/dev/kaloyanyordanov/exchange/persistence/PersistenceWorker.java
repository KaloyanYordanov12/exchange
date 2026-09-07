package dev.kaloyanyordanov.exchange.persistence;

import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.AccountUpdated;
import dev.kaloyanyordanov.exchange.engine.AsyncEventConsumer;
import dev.kaloyanyordanov.exchange.engine.CashDeposited;
import dev.kaloyanyordanov.exchange.engine.CashWithdrawn;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists the engine's audit stream to Postgres, asynchronously and off the hot
 * path. The matching thread only enqueues events into a bounded buffer (a
 * non-blocking {@link #publish}); a dedicated worker thread drains batches and
 * writes them in one transaction. A slow or paused database backs up only this
 * worker's buffer — it never slows matching.
 */
public final class PersistenceWorker implements EventPublisher {

  private final AsyncEventConsumer consumer;
  private final AccountRepository accountRepository;
  private final OrderRepository orderRepository;
  private final TradeRepository tradeRepository;
  private final LedgerTransactionRepository ledgerTransactionRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * Creates the worker.
   *
   * @param accountRepository            the accounts repository
   * @param orderRepository             the orders repository
   * @param tradeRepository             the trades repository
   * @param ledgerTransactionRepository the cash-movement audit repository
   * @param transactionManager          the transaction manager
   * @param capacity                    the bounded buffer capacity
   * @param maxBatch                    the maximum events written per transaction
   */
  public PersistenceWorker(
      AccountRepository accountRepository,
      OrderRepository orderRepository,
      TradeRepository tradeRepository,
      LedgerTransactionRepository ledgerTransactionRepository,
      PlatformTransactionManager transactionManager,
      int capacity,
      int maxBatch) {
    this.accountRepository = accountRepository;
    this.orderRepository = orderRepository;
    this.tradeRepository = tradeRepository;
    this.ledgerTransactionRepository = ledgerTransactionRepository;
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
    List<AccountEntity> accounts = new ArrayList<>();
    List<LedgerTransactionEntity> ledgerTransactions = new ArrayList<>();
    Instant now = Instant.now();
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
        case AccountUpdated updated ->
            accounts.add(
                new AccountEntity(updated.accountId(), updated.cash(), updated.asset()));
        case CashDeposited deposited ->
            ledgerTransactions.add(
                new LedgerTransactionEntity(
                    LedgerTransactionType.DEPOSIT,
                    deposited.accountId(),
                    deposited.amount(),
                    deposited.newCashBalance(),
                    deposited.providerReference(),
                    now));
        case CashWithdrawn withdrawn ->
            ledgerTransactions.add(
                new LedgerTransactionEntity(
                    LedgerTransactionType.WITHDRAWAL,
                    withdrawn.accountId(),
                    withdrawn.amount(),
                    withdrawn.newCashBalance(),
                    withdrawn.providerReference(),
                    now));
        default -> {
          // OrderRejected and BookChanged are not part of the audit log.
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
          if (!accounts.isEmpty()) {
            accountRepository.saveAll(accounts);
          }
          if (!ledgerTransactions.isEmpty()) {
            ledgerTransactionRepository.saveAll(ledgerTransactions);
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
