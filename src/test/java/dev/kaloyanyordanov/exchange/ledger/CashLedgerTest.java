package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CashLedgerTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private CashLedger ledger;

  @BeforeEach
  void setUp() {
    ledger = new CashLedger(1024);
    ledger.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    ledger.stop();
  }

  private CashAccount account(long accountId) {
    CashSnapshot snapshot = ledger.snapshot(TIMEOUT).orElseThrow();
    return snapshot.accounts().stream()
        .filter(a -> a.accountId() == accountId)
        .findFirst()
        .orElse(new CashAccount(accountId, 0L, 0L));
  }

  private CashSnapshot snapshot() {
    return ledger.snapshot(TIMEOUT).orElseThrow();
  }

  @Test
  void depositCreditsAvailable() {
    assertThat(ledger.deposit(1L, 500L, "d1")).isTrue();
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 500L, 0L));
    assertThat(snapshot().cumulativeDeposited()).isEqualTo(500L);
    assertThat(snapshot().totalCash()).isEqualTo(500L);
  }

  @Test
  void withdrawDebitsAvailableWhenFunded() {
    ledger.deposit(1L, 500L, "d1");
    assertThat(ledger.withdraw(1L, 200L, "w1", TIMEOUT)).isEqualTo(WithdrawalResult.APPLIED);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 300L, 0L));
    assertThat(snapshot().cumulativeWithdrawn()).isEqualTo(200L);
  }

  @Test
  void withdrawIsRejectedWhenInsufficient() {
    ledger.deposit(1L, 100L, "d1");
    assertThat(ledger.withdraw(1L, 500L, "w1", TIMEOUT))
        .isEqualTo(WithdrawalResult.INSUFFICIENT_FUNDS);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 100L, 0L));
    assertThat(snapshot().cumulativeWithdrawn()).isZero();
  }

  @Test
  void withdrawCannotTouchReservedFunds() {
    ledger.deposit(1L, 100L, "d1");
    assertThat(ledger.reserve(1L, 80L, TIMEOUT)).isEqualTo(ReservationOutcome.RESERVED);
    // Only 20 is available; the 80 reserved against an open order is untouchable.
    assertThat(ledger.withdraw(1L, 50L, "w1", TIMEOUT))
        .isEqualTo(WithdrawalResult.INSUFFICIENT_FUNDS);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 20L, 80L));
  }

  @Test
  void reserveMovesAvailableToReserved() {
    ledger.deposit(1L, 100L, "d1");
    assertThat(ledger.reserve(1L, 60L, TIMEOUT)).isEqualTo(ReservationOutcome.RESERVED);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 40L, 60L));
    assertThat(snapshot().totalCash()).isEqualTo(100L);
  }

  @Test
  void reserveIsRejectedWhenInsufficient() {
    ledger.deposit(1L, 50L, "d1");
    assertThat(ledger.reserve(1L, 60L, TIMEOUT)).isEqualTo(ReservationOutcome.INSUFFICIENT_FUNDS);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 50L, 0L));
  }

  @Test
  void releaseReturnsReservedToAvailable() {
    ledger.deposit(1L, 100L, "d1");
    ledger.reserve(1L, 60L, TIMEOUT);
    ledger.release(1L, 60L);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 100L, 0L));
  }

  @Test
  void settleMovesReservedFromBuyerToSellerAvailable() {
    ledger.deposit(1L, 100L, "d1");
    ledger.reserve(1L, 60L, TIMEOUT);
    ledger.settle(1L, 2L, 60L);
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 40L, 0L));
    assertThat(account(2L)).isEqualTo(new CashAccount(2L, 60L, 0L));
    // Cash only moved between accounts and buckets: total is conserved.
    assertThat(snapshot().totalCash()).isEqualTo(100L);
  }

  @Test
  void settleWithPriceImprovementReleasesTheSavings() {
    // Buyer reserves 100 (limit 10 x qty 10) but fills 10 units at price 8.
    ledger.deposit(1L, 100L, "d1");
    ledger.reserve(1L, 100L, TIMEOUT);
    ledger.settle(1L, 2L, 80L); // actual cost
    ledger.release(1L, 20L); // savings back to buyer
    assertThat(account(1L)).isEqualTo(new CashAccount(1L, 20L, 0L));
    assertThat(account(2L)).isEqualTo(new CashAccount(2L, 80L, 0L));
    assertThat(snapshot().totalCash()).isEqualTo(100L);
  }

  @Test
  void rejectsNonPositiveAmounts() {
    assertThatThrownBy(() -> ledger.deposit(1L, 0L, "d"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ledger.reserve(1L, -1L, TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ledger.settle(1L, 2L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ledger.withdraw(1L, -5L, "w", TIMEOUT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void operationsOnStoppedLedgerAreUnavailable() throws InterruptedException {
    ledger.stop();
    assertThat(ledger.deposit(1L, 100L, "d")).isFalse();
    assertThat(ledger.reserve(1L, 100L, Duration.ofMillis(200)))
        .isEqualTo(ReservationOutcome.UNAVAILABLE);
    assertThat(ledger.withdraw(1L, 100L, "w", Duration.ofMillis(200)))
        .isEqualTo(WithdrawalResult.UNAVAILABLE);
    assertThat(ledger.snapshot(Duration.ofMillis(200))).isEmpty();
  }
}
