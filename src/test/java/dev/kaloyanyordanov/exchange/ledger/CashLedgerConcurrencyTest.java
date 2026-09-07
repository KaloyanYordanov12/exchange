package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the cross-pair cash model is race-free: many producer threads (standing in
 * for the five engines' matching threads) hammer <em>one</em> account's shared cash
 * concurrently, and the single-owner cash-ledger thread keeps it conserved, never
 * double-spent, never negative. This is the §4.4 concurrency proof for M1.
 */
class CashLedgerConcurrencyTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private CashLedger ledger;

  @BeforeEach
  void setUp() {
    ledger = new CashLedger(1 << 16);
    ledger.start();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    ledger.stop();
  }

  private CashSnapshot snapshot() {
    return ledger.snapshot(TIMEOUT).orElseThrow();
  }

  private static void awaitAll(List<Thread> threads) throws InterruptedException {
    for (Thread thread : threads) {
      thread.join();
    }
  }

  @Test
  void concurrentReservesNeverDoubleSpendTheSameCash() throws InterruptedException {
    long unit = 100L;
    int fundedCount = 200; // exactly enough for 200 reservations
    int contenders = 1_000; // five times as many try
    ledger.deposit(0L, unit * fundedCount, "seed");
    // Force the deposit to be applied before the race starts.
    assertThat(snapshot().totalCash()).isEqualTo(unit * fundedCount);

    ConcurrentLinkedQueue<ReservationOutcome> outcomes = new ConcurrentLinkedQueue<>();
    CountDownLatch gate = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < contenders; i++) {
      threads.add(
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    awaitQuietly(gate);
                    outcomes.add(ledger.reserve(0L, unit, TIMEOUT));
                  }));
    }
    threads.forEach(Thread::start);
    gate.countDown();
    awaitAll(threads);

    long reserved = outcomes.stream().filter(o -> o == ReservationOutcome.RESERVED).count();
    long rejected =
        outcomes.stream().filter(o -> o == ReservationOutcome.INSUFFICIENT_FUNDS).count();
    assertThat(reserved).isEqualTo(fundedCount);
    assertThat(rejected).isEqualTo(contenders - fundedCount);

    CashSnapshot after = snapshot();
    CashAccount account = accountOf(after, 0L);
    assertThat(account.available()).isZero();
    assertThat(account.reserved()).isEqualTo(unit * fundedCount);
    assertThat(after.totalCash()).isEqualTo(unit * fundedCount);
  }

  @Test
  void concurrentCrossPairTradesByOneAccountConserveCash() throws InterruptedException {
    long deposit = 10_000_000L;
    int pairs = 5;
    int tradesPerPair = 500;
    ledger.deposit(0L, deposit, "seed");
    assertThat(snapshot().totalCash()).isEqualTo(deposit);

    CountDownLatch gate = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();
    for (int pair = 0; pair < pairs; pair++) {
      long sellerAccount = 100L + pair; // a distinct counterparty per pair
      threads.add(
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    awaitQuietly(gate);
                    for (int t = 0; t < tradesPerPair; t++) {
                      // A buy on this pair: reserve the max cost, then fill it.
                      long cost = 100L;
                      if (ledger.reserve(0L, cost, TIMEOUT) == ReservationOutcome.RESERVED) {
                        long settled = cost - (t % 2); // sometimes a 1-unit price improvement
                        ledger.settle(0L, sellerAccount, settled);
                        ledger.release(0L, cost - settled); // savings back to the buyer
                      }
                    }
                  }));
    }
    threads.forEach(Thread::start);
    gate.countDown();
    awaitAll(threads);

    CashSnapshot after = snapshot();
    // 1) Conservation: no cash created or destroyed (no withdrawals happened).
    assertThat(after.totalCash()).isEqualTo(deposit);
    assertThat(after.cumulativeDeposited()).isEqualTo(deposit);
    assertThat(after.cumulativeWithdrawn()).isZero();
    // 2) Every reservation was fully settled+released, so nothing stays reserved.
    assertThat(after.totalReserved()).isZero();
    // 3) No account is ever negative.
    assertThat(after.accounts())
        .allSatisfy(
            a -> {
              assertThat(a.available()).isGreaterThanOrEqualTo(0L);
              assertThat(a.reserved()).isGreaterThanOrEqualTo(0L);
            });
    // 4) The buyer's spend equals the sellers' receipts (no double-spend, no loss).
    long buyerAvailable = accountOf(after, 0L).available();
    long sellersReceived =
        after.accounts().stream()
            .filter(a -> a.accountId() >= 100L)
            .mapToLong(CashAccount::available)
            .sum();
    assertThat(buyerAvailable + sellersReceived).isEqualTo(deposit);
  }

  private static CashAccount accountOf(CashSnapshot snapshot, long accountId) {
    return snapshot.accounts().stream()
        .filter(a -> a.accountId() == accountId)
        .findFirst()
        .orElse(new CashAccount(accountId, 0L, 0L));
  }

  private static void awaitQuietly(CountDownLatch gate) {
    try {
      gate.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
