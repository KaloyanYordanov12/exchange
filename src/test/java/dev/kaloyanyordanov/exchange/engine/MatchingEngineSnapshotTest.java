package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.ledger.Account;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchingEngineSnapshotTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private static Account accountById(EngineSnapshot snapshot, long id) {
    return snapshot.accounts().stream()
        .filter(account -> account.id() == id)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void snapshotCapturesInitialTotalsAndPostTradeState() throws InterruptedException {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 10_000L, 0L);
    ledger.deposit(2L, 0L, 100L);
    MatchingEngine engine =
        new MatchingEngine(SYMBOL, 1024, new RecordingEventPublisher(), ledger, ledger);
    engine.start();

    EngineSnapshot initial = engine.requestSnapshot(Duration.ofSeconds(2)).orElseThrow();
    assertThat(initial.initialTotalCash()).isEqualTo(10_000L);
    assertThat(initial.initialTotalAsset()).isEqualTo(100L);
    assertThat(initial.cumulativeCashFromBuyers()).isZero();
    assertThat(initial.accounts()).hasSize(2);
    assertThat(initial.restingOrders()).isEmpty();

    // Crossing pair: account 2 sells 5, account 1 buys 5 at price 100.
    engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 2L));
    engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 1L));

    EngineSnapshot after = engine.requestSnapshot(Duration.ofSeconds(2)).orElseThrow();
    engine.stop();

    // Cumulative trade totals: 100 * 5 = 500 cash, 5 asset, equal on both sides.
    assertThat(after.cumulativeCashFromBuyers()).isEqualTo(500L);
    assertThat(after.cumulativeCashToSellers()).isEqualTo(500L);
    assertThat(after.cumulativeAssetFromSellers()).isEqualTo(5L);
    assertThat(after.cumulativeAssetToBuyers()).isEqualTo(5L);
    // Settled balances.
    assertThat(accountById(after, 1L)).isEqualTo(new Account(1L, 9_500L, 5L));
    assertThat(accountById(after, 2L)).isEqualTo(new Account(2L, 500L, 95L));
    // Fully matched: book empty.
    assertThat(after.restingOrders()).isEmpty();
    assertThat(after.book().bids()).isEmpty();
    assertThat(after.book().asks()).isEmpty();
  }

  @Test
  void snapshotRequestOnStoppedEngineIsEmpty() throws InterruptedException {
    MatchingEngine engine = new MatchingEngine(SYMBOL, 16, new RecordingEventPublisher());
    engine.start();
    engine.stop();
    assertThat(engine.requestSnapshot(Duration.ofMillis(200))).isEmpty();
  }
}
