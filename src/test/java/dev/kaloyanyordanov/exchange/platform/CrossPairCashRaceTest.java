package dev.kaloyanyordanov.exchange.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The §4.4 proof at the engine level: one account trades concurrently on two
 * independent engines that share the single {@link CashLedger}. Because every engine
 * settles cash only by message-passing to that one actor, the account's shared cash
 * is never raced, double-spent, or driven negative - even though two matching threads
 * are spending it at once.
 */
class CrossPairCashRaceTest {

  private static final Symbol PAIR_A = new Symbol("AAA", "USD", 1L, 1L);
  private static final Symbol PAIR_B = new Symbol("BBB", "USD", 1L, 1L);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final long BUYER = 0L;
  private static final long SELLER_A = 100L;
  private static final long SELLER_B = 200L;
  private static final long PRICE = 100L;

  @Test
  void concurrentBuysAcrossTwoEnginesNeverRaceTheSharedCash() throws InterruptedException {
    CashLedger cash = new CashLedger(1 << 16);
    cash.start();

    AssetLedger assetA = new AssetLedger();
    AssetLedger assetB = new AssetLedger();
    assetA.endow(SELLER_A, 2_000L);
    assetB.endow(SELLER_B, 2_000L);
    EventPublisher noop = event -> {};
    MatchingEngine engineA = new MatchingEngine(PAIR_A, 1 << 16, noop, assetA, cash, 0L);
    MatchingEngine engineB = new MatchingEngine(PAIR_B, 1 << 16, noop, assetB, cash, 1_000_000L);
    engineA.start();
    engineB.start();

    long deposit = 1_000_000L;
    cash.deposit(BUYER, deposit, "seed");
    // Force the deposit to be applied.
    assertThat(cash.snapshot(TIMEOUT).orElseThrow().totalCash()).isEqualTo(deposit);

    // Resting liquidity: each seller offers 2,000 units at PRICE (more than the buyer
    // will consume, so every buy fills and none rests holding a reservation).
    engineA.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, PRICE, 2_000L, SELLER_A));
    engineB.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, PRICE, 2_000L, SELLER_B));

    int threadsPerPair = 5;
    int buysPerThread = 200;
    final long buysPerPair = (long) threadsPerPair * buysPerThread;
    AtomicLong orderIds = new AtomicLong(1000L);
    CountDownLatch gate = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();
    for (int pair = 0; pair < 2; pair++) {
      MatchingEngine engine = pair == 0 ? engineA : engineB;
      for (int t = 0; t < threadsPerPair; t++) {
        threads.add(
            Thread.ofVirtual()
                .unstarted(
                    () -> {
                      awaitQuietly(gate);
                      for (int b = 0; b < buysPerThread; b++) {
                        if (cash.reserve(BUYER, PRICE, TIMEOUT) != ReservationOutcome.RESERVED) {
                          continue;
                        }
                        SubmitOrder order =
                            new SubmitOrder(
                                OrderId.of(orderIds.incrementAndGet()),
                                Side.BUY,
                                PRICE,
                                1L,
                                BUYER);
                        while (engine.submit(order) != SubmitResult.ENQUEUED) {
                          Thread.onSpinWait();
                        }
                      }
                    }));
      }
    }
    threads.forEach(Thread::start);
    gate.countDown();
    for (Thread thread : threads) {
      thread.join();
    }

    // Let the engines drain their fills and the cash ledger apply the settlements.
    engineA.stop();
    engineB.stop();
    CashSnapshot snapshot = cash.snapshot(TIMEOUT).orElseThrow();
    cash.stop();

    long spentPerPair = buysPerPair * PRICE;
    // 1) Conservation: no cash created or destroyed.
    assertThat(snapshot.totalCash()).isEqualTo(deposit);
    assertThat(snapshot.cumulativeDeposited()).isEqualTo(deposit);
    // 2) Every buy filled, so nothing stays reserved and no balance is negative.
    assertThat(snapshot.totalReserved()).isZero();
    assertThat(snapshot.accounts())
        .allSatisfy(
            account -> {
              assertThat(account.available()).isGreaterThanOrEqualTo(0L);
              assertThat(account.reserved()).isGreaterThanOrEqualTo(0L);
            });
    // 3) No double-spend: the buyer spent exactly the two pairs' fills, and each
    //    seller received exactly its pair's proceeds.
    assertThat(accountOf(snapshot, BUYER).available()).isEqualTo(deposit - 2L * spentPerPair);
    assertThat(accountOf(snapshot, SELLER_A).available()).isEqualTo(spentPerPair);
    assertThat(accountOf(snapshot, SELLER_B).available()).isEqualTo(spentPerPair);
    // 4) The buyer received the asset it paid for, on each independent engine.
    assertThat(assetA.assetOf(BUYER)).isEqualTo(buysPerPair);
    assertThat(assetB.assetOf(BUYER)).isEqualTo(buysPerPair);
  }

  private static CashAccount accountOf(CashSnapshot snapshot, long accountId) {
    return snapshot.accounts().stream()
        .filter(account -> account.accountId() == accountId)
        .findFirst()
        .orElseThrow();
  }

  private static void awaitQuietly(CountDownLatch gate) {
    try {
      gate.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
