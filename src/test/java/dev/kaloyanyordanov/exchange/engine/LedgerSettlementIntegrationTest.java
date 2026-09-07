package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import org.junit.jupiter.api.Test;

/**
 * Settlement runs on the matching thread through the ledger-backed fill policy.
 * These end-to-end checks confirm balances settle correctly and conserve, and
 * that an under-funded order fills only up to what it can afford.
 */
class LedgerSettlementIntegrationTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);
  private static final long BUYER = 100L;
  private static final long SELLER = 200L;

  @Test
  void fundedCrossSettlesAndConserves() throws InterruptedException {
    Ledger ledger = new Ledger();
    ledger.deposit(BUYER, 10_000L, 0L);
    ledger.deposit(SELLER, 0L, 100L);
    final long totalCash = ledger.totalCash();
    final long totalAsset = ledger.totalAsset();

    MatchingEngine engine =
        new MatchingEngine(SYMBOL, 1024, new RecordingEventPublisher(), ledger);
    engine.start();
    engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 10L, SELLER));
    engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 10L, BUYER));
    engine.stop();

    assertThat(ledger.cashOf(BUYER)).isEqualTo(9_000L);
    assertThat(ledger.assetOf(BUYER)).isEqualTo(10L);
    assertThat(ledger.cashOf(SELLER)).isEqualTo(1_000L);
    assertThat(ledger.assetOf(SELLER)).isEqualTo(90L);
    assertThat(ledger.totalCash()).isEqualTo(totalCash);
    assertThat(ledger.totalAsset()).isEqualTo(totalAsset);
    assertThat(engine.isCrossed()).isFalse();
    assertThat(engine.restingOrders()).isEmpty();
  }

  @Test
  void underFundedBuyerFillsOnlyWhatItCanAfford() throws InterruptedException {
    Ledger ledger = new Ledger();
    ledger.deposit(BUYER, 500L, 0L); // can afford only 5 units at price 100
    ledger.deposit(SELLER, 0L, 100L);

    MatchingEngine engine =
        new MatchingEngine(SYMBOL, 1024, new RecordingEventPublisher(), ledger);
    engine.start();
    engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 10L, SELLER));
    engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 10L, BUYER));
    engine.stop();

    // Buyer spent all 500 on 5 units; no balance negative.
    assertThat(ledger.cashOf(BUYER)).isZero();
    assertThat(ledger.assetOf(BUYER)).isEqualTo(5L);
    assertThat(ledger.cashOf(SELLER)).isEqualTo(500L);
    assertThat(ledger.assetOf(SELLER)).isEqualTo(95L);

    // The unaffordable remainder was rejected, not rested: only the seller's
    // remaining 5 units rest, and the book is not crossed.
    assertThat(engine.isCrossed()).isFalse();
    assertThat(engine.restingOrders())
        .singleElement()
        .satisfies(
            order -> {
              assertThat(order.side()).isEqualTo(Side.SELL);
              assertThat(order.remaining()).isEqualTo(5L);
            });
    assertThat(engine.restingOrders()).extracting(Order::id).containsExactly(OrderId.of(1L));
  }
}
