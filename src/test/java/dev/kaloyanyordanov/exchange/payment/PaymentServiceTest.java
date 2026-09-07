package dev.kaloyanyordanov.exchange.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private Ledger ledger;
  private MatchingEngine engine;
  private PaymentService service;

  @BeforeEach
  void setUp() {
    ledger = new Ledger();
    engine = new MatchingEngine(SYMBOL, 1024, event -> {}, ledger, ledger);
    service = new PaymentService(new DemoPaymentProvider(), engine, TIMEOUT);
    engine.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    engine.stop();
  }

  /** Forces the deposit to be applied by draining a snapshot request behind it. */
  private void awaitApplied() {
    engine.requestSnapshot(TIMEOUT).orElseThrow();
  }

  @Test
  void depositAuthorizesAndCredits() {
    FundingResult result = service.deposit(1L, 1_000L);
    awaitApplied();

    assertThat(result.status()).isEqualTo(FundingStatus.ACCEPTED);
    assertThat(result.reference()).startsWith("demo-deposit-");
    assertThat(ledger.cashOf(1L)).isEqualTo(1_000L);
  }

  @Test
  void withdrawalDebitsWhenFunded() {
    service.deposit(1L, 1_000L);
    awaitApplied();

    FundingResult result = service.withdraw(1L, 400L);

    assertThat(result.status()).isEqualTo(FundingStatus.APPLIED);
    assertThat(result.reference()).startsWith("demo-withdrawal-");
    assertThat(ledger.cashOf(1L)).isEqualTo(600L);
  }

  @Test
  void withdrawalReportsInsufficientFundsWithoutMutating() {
    service.deposit(1L, 100L);
    awaitApplied();

    FundingResult result = service.withdraw(1L, 500L);

    assertThat(result.status()).isEqualTo(FundingStatus.INSUFFICIENT_FUNDS);
    assertThat(ledger.cashOf(1L)).isEqualTo(100L);
  }

  @Test
  void providerDeclineLeavesBalanceUntouched() {
    FundingResult deposit = service.deposit(1L, -5L);
    FundingResult withdrawal = service.withdraw(1L, -5L);
    awaitApplied();

    assertThat(deposit.status()).isEqualTo(FundingStatus.PROVIDER_DECLINED);
    assertThat(withdrawal.status()).isEqualTo(FundingStatus.PROVIDER_DECLINED);
    assertThat(ledger.cashOf(1L)).isZero();
  }

  @Test
  void fundingIsUnavailableOnceTheEngineStops() throws InterruptedException {
    engine.stop();

    assertThat(service.deposit(1L, 1_000L).status()).isEqualTo(FundingStatus.UNAVAILABLE);
    assertThat(service.withdraw(1L, 1_000L).status()).isEqualTo(FundingStatus.UNAVAILABLE);
  }

  @Test
  void constructorRejectsNulls() {
    assertThatThrownBy(() -> new PaymentService(null, engine, TIMEOUT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PaymentService(new DemoPaymentProvider(), null, TIMEOUT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PaymentService(new DemoPaymentProvider(), engine, null))
        .isInstanceOf(NullPointerException.class);
  }
}
