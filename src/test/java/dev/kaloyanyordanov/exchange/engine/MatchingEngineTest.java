package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private static MatchingEngine engine(RecordingEventPublisher publisher) {
    return new MatchingEngine(SYMBOL, 1024, publisher);
  }

  @Test
  void constructorValidatesArguments() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    assertThatThrownBy(() -> new MatchingEngine(null, 16, publisher))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MatchingEngine(SYMBOL, 0, publisher))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MatchingEngine(SYMBOL, 16, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void ingressCapacityIsRoundedToPowerOfTwo() {
    assertThat(engine(new RecordingEventPublisher()).ingressCapacity()).isEqualTo(1024);
    assertThat(new MatchingEngine(SYMBOL, 100, new RecordingEventPublisher()).ingressCapacity())
        .isEqualTo(128);
  }

  @Test
  void submitBeforeStartIsRejectedNotRunning() {
    MatchingEngine engine = engine(new RecordingEventPublisher());
    SubmitResult result = engine.submit(new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 5L, 1L));
    assertThat(result).isEqualTo(SubmitResult.REJECTED_NOT_RUNNING);
  }

  @Test
  void nonCrossingSubmitRestsAndEmitsAcceptedAndBookChanged() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = engine(publisher);

    engine.processCommand(new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 5L, 7L));

    assertThat(publisher.accepted())
        .singleElement()
        .satisfies(
            accepted -> {
              assertThat(accepted.id()).isEqualTo(OrderId.of(1L));
              assertThat(accepted.sequence()).isZero();
            });
    assertThat(publisher.trades()).isEmpty();
    assertThat(publisher.events()).last().isInstanceOf(BookChanged.class);
    assertThat(engine.restingOrders()).extracting(Order::id).containsExactly(OrderId.of(1L));
  }

  @Test
  void crossingSubmitProducesTradeAndAssignsSequencesInOrder() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = engine(publisher);

    engine.processCommand(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 10L));
    engine.processCommand(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 20L));

    assertThat(publisher.acceptedSequenceOf(OrderId.of(1L))).isZero();
    assertThat(publisher.acceptedSequenceOf(OrderId.of(2L))).isEqualTo(1L);

    assertThat(publisher.trades()).singleElement().satisfies(
        executed -> {
          Trade trade = executed.trade();
          assertThat(trade.price()).isEqualTo(100L);
          assertThat(trade.quantity()).isEqualTo(5L);
          assertThat(trade.buyOrderId()).isEqualTo(OrderId.of(2L));
          assertThat(trade.sellOrderId()).isEqualTo(OrderId.of(1L));
          assertThat(trade.sequence()).isZero();
        });
    assertThat(engine.isCrossed()).isFalse();
    assertThat(engine.snapshot().bids()).isEmpty();
    assertThat(engine.snapshot().asks()).isEmpty();
  }

  @Test
  void tradeSequenceContinuesAcrossCommands() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = engine(publisher);

    engine.processCommand(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 3L, 1L));
    engine.processCommand(new SubmitOrder(OrderId.of(2L), Side.SELL, 101L, 3L, 1L));
    engine.processCommand(new SubmitOrder(OrderId.of(3L), Side.BUY, 105L, 6L, 2L));

    assertThat(publisher.trades())
        .extracting(executed -> executed.trade().sequence())
        .containsExactly(0L, 1L);
  }

  @Test
  void startTwiceIsRejected() throws InterruptedException {
    MatchingEngine engine = engine(new RecordingEventPublisher());
    engine.start();
    try {
      assertThatThrownBy(engine::start).isInstanceOf(IllegalStateException.class);
    } finally {
      engine.stop();
    }
  }

  @Test
  void lifecycleProcessesEnqueuedCommand() throws InterruptedException {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = engine(publisher);
    engine.start();
    assertThat(engine.submit(new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 5L, 1L)))
        .isEqualTo(SubmitResult.ENQUEUED);
    engine.stop();

    assertThat(publisher.accepted()).extracting(OrderAccepted::id).containsExactly(OrderId.of(1L));
    assertThat(engine.restingOrders()).extracting(Order::id).containsExactly(OrderId.of(1L));
  }

  @Test
  void emitsAccountUpdatesForAffectedAccountsWhenLedgerBacked() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    Ledger ledger = new Ledger();
    ledger.deposit(10L, 10_000L, 0L);
    ledger.deposit(20L, 0L, 100L);
    MatchingEngine engine =
        new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);

    engine.processCommand(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 20L));
    engine.processCommand(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 10L));

    // One AccountUpdated per distinct affected account, with settled balances.
    assertThat(publisher.accountUpdates())
        .containsExactlyInAnyOrder(
            new AccountUpdated(10L, 9_500L, 5L), new AccountUpdated(20L, 500L, 95L));
  }

  @Test
  void emitsNoAccountUpdatesWithoutLedger() {
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = engine(publisher);
    engine.processCommand(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 20L));
    engine.processCommand(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 10L));
    assertThat(publisher.accountUpdates()).isEmpty();
  }

  @Test
  void submitAfterStopIsRejectedNotRunning() throws InterruptedException {
    MatchingEngine engine = engine(new RecordingEventPublisher());
    engine.start();
    engine.stop();
    assertThat(engine.submit(new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 5L, 1L)))
        .isEqualTo(SubmitResult.REJECTED_NOT_RUNNING);
  }
}
