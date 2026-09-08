package dev.kaloyanyordanov.exchange.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.api.PlacementOutcome;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.payment.FundingStatus;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.time.Duration;
import java.util.List;
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
 * Verifies the async workers durably record orders and trades (the persistence
 * worker) and cash movements (the cash audit worker) to a real Postgres via the
 * Flyway-managed schema. Excluded from PIT (slow full context + container); the fast
 * mapping unit tests carry mutation coverage.
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

  private static final String PAIR = "DOGE-USD";

  @Autowired private ExchangeRegistry registry;
  @Autowired private PaymentService paymentService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private TradeRepository tradeRepository;
  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;

  @Test
  @Order(1)
  void persistsOrdersAndTrades() {
    // Bob (account 2) sells; Alice (account 1) reserves and buys, crossing on DOGE-USD
    // (tick 1, so price 100 is valid and affordable against the genesis cash).
    assertThat(registry.place(PAIR, 2L, Side.SELL, 100L, 10L).status())
        .isEqualTo(PlacementOutcome.Status.ACCEPTED);
    assertThat(registry.place(PAIR, 1L, Side.BUY, 100L, 10L).status())
        .isEqualTo(PlacementOutcome.Status.ACCEPTED);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(tradeRepository.count()).isEqualTo(1L));

    assertThat(orderRepository.count()).isEqualTo(2L);
    TradeEntity trade = tradeRepository.findAll().get(0);
    assertThat(trade.getPrice()).isEqualTo(100L);
    assertThat(trade.getQuantity()).isEqualTo(10L);
  }

  @Test
  @Order(2)
  void auditsDepositsAndWithdrawalsToTheLedgerLog() {
    // A dedicated account (3, not a configured genesis account) isolates these
    // movements from the genesis funding; the deposit funds the withdrawal.
    assertThat(paymentService.deposit(3L, 5_000L).status()).isEqualTo(FundingStatus.ACCEPTED);
    assertThat(paymentService.withdraw(3L, 2_000L).status()).isEqualTo(FundingStatus.APPLIED);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(rowsForAccount(3L)).hasSize(2));

    LedgerTransactionEntity deposit =
        rowsForAccount(3L).stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.DEPOSIT)
            .findFirst()
            .orElseThrow();
    assertThat(deposit.getAmount()).isEqualTo(5_000L);
    assertThat(deposit.getProviderReference()).startsWith("demo-deposit-");
    assertThat(deposit.getCreatedAt()).isNotNull();

    LedgerTransactionEntity withdrawal =
        rowsForAccount(3L).stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.WITHDRAWAL)
            .findFirst()
            .orElseThrow();
    assertThat(withdrawal.getAmount()).isEqualTo(2_000L);
    assertThat(withdrawal.getProviderReference()).startsWith("demo-withdrawal-");
  }

  private List<LedgerTransactionEntity> rowsForAccount(long accountId) {
    return ledgerTransactionRepository.findAll().stream()
        .filter(row -> row.getAccountId() == accountId)
        .toList();
  }
}
