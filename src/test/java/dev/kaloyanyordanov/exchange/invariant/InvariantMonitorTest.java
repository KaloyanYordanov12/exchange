package dev.kaloyanyordanov.exchange.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import org.junit.jupiter.api.Test;

class InvariantMonitorTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private static MatchingEngine fundedEngine() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 1_000_000L, 1_000L);
    ledger.deposit(2L, 1_000_000L, 1_000L);
    EventPublisher noop = event -> {};
    return new MatchingEngine(SYMBOL, 1024, noop, ledger, ledger);
  }

  @Test
  void rejectsInvalidConfiguration() {
    MatchingEngine engine = fundedEngine();
    assertThatThrownBy(() -> new InvariantMonitor(engine, 0L, 1000L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InvariantMonitor(engine, 1000L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsAllInvariantsHoldOnLiveEngine() throws InterruptedException {
    MatchingEngine engine = fundedEngine();
    engine.start();
    InvariantMonitor monitor = new InvariantMonitor(engine, 1000L, 2000L);

    InvariantReport initial = monitor.checkNow();
    assertThat(initial.available()).isTrue();
    assertThat(initial.allPassed()).isTrue();
    assertThat(monitor.latest()).isEqualTo(initial);

    // Trade, then check again: invariants still hold on real state.
    engine.submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 2L));
    engine.submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 1L));

    InvariantReport afterTrade = monitor.checkNow();
    engine.stop();
    assertThat(afterTrade.available()).isTrue();
    assertThat(afterTrade.allPassed()).isTrue();
    assertThat(afterTrade.results()).hasSize(7);
  }

  @Test
  void reportsUnavailableWhenNoSnapshotCanBeObtained() throws InterruptedException {
    MatchingEngine engine = fundedEngine();
    engine.start();
    engine.stop();
    InvariantMonitor monitor = new InvariantMonitor(engine, 1000L, 200L);

    InvariantReport report = monitor.checkNow();
    assertThat(report.available()).isFalse();
    assertThat(report.allPassed()).isFalse();
  }
}
