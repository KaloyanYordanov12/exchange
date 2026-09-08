package dev.kaloyanyordanov.exchange.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InvariantMonitorTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  /** A started cash ledger and asset-backed engine over the same accounts. */
  private record Setup(MatchingEngine engine, CashLedger cash) {}

  private static Setup startedSetup() {
    CashLedger cash = new CashLedger(1024);
    cash.start();
    AssetLedger asset = new AssetLedger();
    asset.endow(1L, 1_000L);
    asset.endow(2L, 1_000L);
    cash.deposit(1L, 1_000_000L, "seed");
    cash.deposit(2L, 1_000_000L, "seed");
    EventPublisher noop = event -> {};
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, noop, asset, cash);
    return new Setup(engine, cash);
  }

  @Test
  void rejectsInvalidConfiguration() {
    Setup setup = startedSetup();
    try {
      assertThatThrownBy(() -> new InvariantMonitor(setup.engine(), setup.cash(), 0L, 1000L))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new InvariantMonitor(setup.engine(), setup.cash(), 1000L, 0L))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      quietStop(setup.cash());
    }
  }

  @Test
  void reportsAllInvariantsHoldOnLiveEngine() throws InterruptedException {
    Setup setup = startedSetup();
    setup.engine().start();
    try {
      InvariantMonitor monitor = new InvariantMonitor(setup.engine(), setup.cash(), 1000L, 2000L);

      InvariantReport initial = monitor.checkNow();
      assertThat(initial.available()).isTrue();
      assertThat(initial.allPassed()).isTrue();
      assertThat(monitor.latest()).isEqualTo(initial);
      // Six per-pair invariants plus two cross-account cash invariants.
      assertThat(initial.results()).hasSize(8);

      // Account 1 reserves and buys 5 @ 100 from account 2; invariants still hold.
      assertThat(setup.cash().reserve(1L, 500L, TIMEOUT)).isEqualTo(ReservationOutcome.RESERVED);
      setup.engine().submit(new SubmitOrder(OrderId.of(1L), Side.SELL, 100L, 5L, 2L));
      setup.engine().submit(new SubmitOrder(OrderId.of(2L), Side.BUY, 100L, 5L, 1L));

      InvariantReport afterTrade = monitor.checkNow();
      assertThat(afterTrade.available()).isTrue();
      assertThat(afterTrade.allPassed()).isTrue();
      assertThat(afterTrade.results()).hasSize(8);
    } finally {
      setup.engine().stop();
      quietStop(setup.cash());
    }
  }

  @Test
  void reportsUnavailableWhenNoSnapshotCanBeObtained() throws InterruptedException {
    Setup setup = startedSetup();
    setup.engine().start();
    setup.engine().stop();
    try {
      InvariantMonitor monitor = new InvariantMonitor(setup.engine(), setup.cash(), 1000L, 200L);
      InvariantReport report = monitor.checkNow();
      assertThat(report.available()).isFalse();
      assertThat(report.allPassed()).isFalse();
    } finally {
      quietStop(setup.cash());
    }
  }

  private static void quietStop(CashLedger cash) {
    try {
      cash.stop();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
