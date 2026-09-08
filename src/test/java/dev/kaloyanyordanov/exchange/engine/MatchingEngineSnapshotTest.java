package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchingEngineSnapshotTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private static AccountBalance accountById(EngineSnapshot snapshot, long id) {
    return snapshot.accounts().stream()
        .filter(account -> account.accountId() == id)
        .findFirst()
        .orElseThrow();
  }

  private static CashAccount cashById(CashSnapshot snapshot, long id) {
    return snapshot.accounts().stream()
        .filter(account -> account.accountId() == id)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void snapshotCapturesAssetTotalsAndSettlesCashThroughTheCashLedger()
      throws InterruptedException {
    CashLedger cash = new CashLedger(1024);
    cash.start();
    try {
      AssetLedger asset = new AssetLedger();
      asset.endow(2L, 100L); // account 2 (the seller) holds asset
      cash.deposit(1L, 10_000L, "seed"); // account 1 (the buyer) holds cash
      // The buyer reserves the cost of its buy (5 @ 100) before the order enters.
      assertThat(cash.reserve(1L, 500L, TIMEOUT)).isEqualTo(ReservationOutcome.RESERVED);

      MatchingEngine engine =
          new MatchingEngine(SYMBOL, 1024, new RecordingEventPublisher(), asset, cash);
      engine.start();

      EngineSnapshot initial = engine.requestSnapshot(TIMEOUT).orElseThrow();
      assertThat(initial.initialTotalAsset()).isEqualTo(100L);
      assertThat(initial.accounts()).hasSize(1); // only the endowed seller so far
      assertThat(initial.cumulativeCashFromBuyers()).isZero();

      engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 2L));
      engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 1L));

      EngineSnapshot after = engine.requestSnapshot(TIMEOUT).orElseThrow();
      engine.stop();

      // Trade counters and per-pair asset settlement.
      assertThat(after.cumulativeCashFromBuyers()).isEqualTo(500L);
      assertThat(after.cumulativeCashToSellers()).isEqualTo(500L);
      assertThat(after.cumulativeAssetFromSellers()).isEqualTo(5L);
      assertThat(after.cumulativeAssetToBuyers()).isEqualTo(5L);
      assertThat(accountById(after, 1L)).isEqualTo(new AccountBalance(1L, 5L));
      assertThat(accountById(after, 2L)).isEqualTo(new AccountBalance(2L, 95L));
      assertThat(after.restingOrders()).isEmpty();

      // Cash settled on the shared cash ledger: buyer's reserved 500 moved to seller.
      CashSnapshot cashSnapshot = cash.snapshot(TIMEOUT).orElseThrow();
      assertThat(cashById(cashSnapshot, 1L)).isEqualTo(new CashAccount(1L, 9_500L, 0L));
      assertThat(cashById(cashSnapshot, 2L)).isEqualTo(new CashAccount(2L, 500L, 0L));
      assertThat(cashSnapshot.totalCash()).isEqualTo(10_000L);
    } finally {
      cash.stop();
    }
  }

  @Test
  void snapshotRequestOnStoppedEngineIsEmpty() throws InterruptedException {
    MatchingEngine engine = new MatchingEngine(SYMBOL, 16, new RecordingEventPublisher());
    engine.start();
    engine.stop();
    assertThat(engine.requestSnapshot(Duration.ofMillis(200))).isEmpty();
  }
}
