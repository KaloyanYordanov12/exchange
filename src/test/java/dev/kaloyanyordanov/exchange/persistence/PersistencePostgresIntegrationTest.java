package dev.kaloyanyordanov.exchange.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.payment.FundingStatus;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import java.time.Duration;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the async worker durably records orders, trades, and account snapshots
 * to a real Postgres (via Flyway-managed schema). Excluded from PIT (slow full
 * context + container); the fast mapping unit tests carry mutation coverage.
 */
@SpringBootTest
@ActiveProfiles("persistence")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistencePostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MatchingEngine engine;
  @Autowired private PaymentService paymentService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private TradeRepository tradeRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

  @Test
  @Order(1)
  void persistsOrdersTradesAndAccountSnapshots() {
    // Bob (account 2) sells; Alice (account 1) buys and crosses.
    engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 10L, 2L));
    engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 10L, 1L));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(tradeRepository.count()).isEqualTo(1L));

    assertThat(orderRepository.count()).isEqualTo(2L);
    assertThat(accountRepository.count()).isEqualTo(2L);

    TradeEntity trade = tradeRepository.findAll().get(0);
    assertThat(trade.getPrice()).isEqualTo(100L);
    assertThat(trade.getQuantity()).isEqualTo(10L);
    assertThat(trade.getBuyOrderId()).isEqualTo(2L);
    assertThat(trade.getSellOrderId()).isEqualTo(1L);

    // Buyer settled: cash 100_000_000 - 1_000, asset 1_000 + 10.
    AccountEntity buyer = accountRepository.findById(1L).orElseThrow();
    assertThat(buyer.getCash()).isEqualTo(99_999_000L);
    assertThat(buyer.getAsset()).isEqualTo(1_010L);
  }

  @Test
  @Order(2)
  void persistsDepositsAndWithdrawalsToTheLedgerLog() {
    // A dedicated account (3) so the funding movements do not disturb the trade
    // test's balances; the deposit funds the withdrawal.
    assertThat(paymentService.deposit(3L, 5_000L).status()).isEqualTo(FundingStatus.ACCEPTED);
    assertThat(paymentService.withdraw(3L, 2_000L).status()).isEqualTo(FundingStatus.APPLIED);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(ledgerTransactionRepository.count()).isEqualTo(2L));

    LedgerTransactionEntity deposit =
        ledgerTransactionRepository.findAll().stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.DEPOSIT)
            .findFirst()
            .orElseThrow();
    assertThat(deposit.getAccountId()).isEqualTo(3L);
    assertThat(deposit.getAmount()).isEqualTo(5_000L);
    assertThat(deposit.getProviderReference()).startsWith("demo-deposit-");
    assertThat(deposit.getCreatedAt()).isNotNull();

    LedgerTransactionEntity withdrawal =
        ledgerTransactionRepository.findAll().stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.WITHDRAWAL)
            .findFirst()
            .orElseThrow();
    assertThat(withdrawal.getAmount()).isEqualTo(2_000L);
    assertThat(withdrawal.getProviderReference()).startsWith("demo-withdrawal-");
  }
}
