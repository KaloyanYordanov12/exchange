package dev.kaloyanyordanov.exchange.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private CashLedger cashLedger;
  private PaymentService service;

  @BeforeEach
  void setUp() {
    cashLedger = new CashLedger(1024);
    service = new PaymentService(new DemoPaymentProvider(), cashLedger, TIMEOUT);
    cashLedger.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    cashLedger.stop();
  }

  private long availableOf(long accountId) {
    return cashLedger.snapshot(TIMEOUT).orElseThrow().accounts().stream()
        .filter(account -> account.accountId() == accountId)
        .findFirst()
        .map(CashAccount::available)
        .orElse(0L);
  }

  @Test
  void depositAuthorizesAndCredits() {
    FundingResult result = service.deposit(1L, 1_000L);
    assertThat(result.status()).isEqualTo(FundingStatus.ACCEPTED);
    assertThat(result.reference()).startsWith("demo-deposit-");
    assertThat(availableOf(1L)).isEqualTo(1_000L);
  }

  @Test
  void withdrawalDebitsWhenFunded() {
    service.deposit(1L, 1_000L);
    FundingResult result = service.withdraw(1L, 400L);
    assertThat(result.status()).isEqualTo(FundingStatus.APPLIED);
    assertThat(result.reference()).startsWith("demo-withdrawal-");
    assertThat(availableOf(1L)).isEqualTo(600L);
  }

  @Test
  void withdrawalReportsInsufficientFundsWithoutMutating() {
    service.deposit(1L, 100L);
    FundingResult result = service.withdraw(1L, 500L);
    assertThat(result.status()).isEqualTo(FundingStatus.INSUFFICIENT_FUNDS);
    assertThat(availableOf(1L)).isEqualTo(100L);
  }

  @Test
  void providerDeclineLeavesBalanceUntouched() {
    FundingResult deposit = service.deposit(1L, -5L);
    FundingResult withdrawal = service.withdraw(1L, -5L);
    assertThat(deposit.status()).isEqualTo(FundingStatus.PROVIDER_DECLINED);
    assertThat(withdrawal.status()).isEqualTo(FundingStatus.PROVIDER_DECLINED);
    assertThat(availableOf(1L)).isZero();
  }

  @Test
  void fundingIsUnavailableOnceTheLedgerStops() throws InterruptedException {
    cashLedger.stop();
    assertThat(service.deposit(1L, 1_000L).status()).isEqualTo(FundingStatus.UNAVAILABLE);
    assertThat(service.withdraw(1L, 1_000L).status()).isEqualTo(FundingStatus.UNAVAILABLE);
  }

  @Test
  void constructorRejectsNulls() {
    assertThatThrownBy(() -> new PaymentService(null, cashLedger, TIMEOUT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PaymentService(new DemoPaymentProvider(), null, TIMEOUT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PaymentService(new DemoPaymentProvider(), cashLedger, null))
        .isInstanceOf(NullPointerException.class);
  }
}
