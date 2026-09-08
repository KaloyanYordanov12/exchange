package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.persistence.CashAuditWorker;
import dev.kaloyanyordanov.exchange.persistence.LedgerTransactionRepository;
import dev.kaloyanyordanov.exchange.persistence.OrderRepository;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.persistence.TradeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the async persistence workers, only under the {@code persistence} profile.
 * When the profile is off, no persistence beans exist and the exchange runs fully
 * in-memory, so Postgres is never a matching dependency.
 */
@Configuration
@Profile("persistence")
public class PersistenceConfiguration {

  /**
   * The async order/trade persistence worker, started/stopped with the context.
   *
   * @param orderRepository    the orders repository
   * @param tradeRepository    the trades repository
   * @param transactionManager the transaction manager
   * @param capacity           the worker's bounded buffer capacity
   * @param maxBatch           the maximum events written per transaction
   * @return the persistence worker
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public PersistenceWorker persistenceWorker(
      OrderRepository orderRepository,
      TradeRepository tradeRepository,
      PlatformTransactionManager transactionManager,
      @Value("${exchange.persistence.capacity:65536}") int capacity,
      @Value("${exchange.persistence.max-batch:500}") int maxBatch) {
    return new PersistenceWorker(
        orderRepository, tradeRepository, transactionManager, capacity, maxBatch);
  }

  /**
   * The async cash-movement audit worker (the cash ledger's listener),
   * started/stopped with the context.
   *
   * @param ledgerTransactionRepository the cash-movement audit repository
   * @param transactionManager          the transaction manager
   * @param capacity                    the worker's bounded buffer capacity
   * @param maxBatch                    the maximum rows written per transaction
   * @return the cash audit worker
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public CashAuditWorker cashAuditWorker(
      LedgerTransactionRepository ledgerTransactionRepository,
      PlatformTransactionManager transactionManager,
      @Value("${exchange.persistence.capacity:65536}") int capacity,
      @Value("${exchange.persistence.max-batch:500}") int maxBatch) {
    return new CashAuditWorker(
        ledgerTransactionRepository, transactionManager, capacity, maxBatch);
  }
}
