package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.persistence.AccountRepository;
import dev.kaloyanyordanov.exchange.persistence.OrderRepository;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.persistence.TradeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the async persistence worker — only under the {@code persistence} profile.
 * When the profile is off, no persistence beans exist and the exchange runs fully
 * in-memory, so Postgres is never a matching dependency.
 */
@Configuration
@Profile("persistence")
public class PersistenceConfiguration {

  /**
   * The async persistence worker, started/stopped with the context.
   *
   * @param accountRepository  the accounts repository
   * @param orderRepository    the orders repository
   * @param tradeRepository    the trades repository
   * @param transactionManager the transaction manager
   * @param capacity           the worker's bounded buffer capacity
   * @param maxBatch           the maximum events written per transaction
   * @return the persistence worker
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public PersistenceWorker persistenceWorker(
      AccountRepository accountRepository,
      OrderRepository orderRepository,
      TradeRepository tradeRepository,
      PlatformTransactionManager transactionManager,
      @Value("${exchange.persistence.capacity:65536}") int capacity,
      @Value("${exchange.persistence.max-batch:500}") int maxBatch) {
    return new PersistenceWorker(
        accountRepository,
        orderRepository,
        tradeRepository,
        transactionManager,
        capacity,
        maxBatch);
  }
}
