package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MatchingEngineFundingTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  @Test
  void depositCreditsAccountAndEmitsEvents() {
    Ledger ledger = new Ledger();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);

    engine.processCommand(new DepositCash(1L, 500L, "demo-deposit-1"));

    assertThat(ledger.cashOf(1L)).isEqualTo(500L);
    assertThat(publisher.events())
        .contains(new CashDeposited(1L, 500L, 500L, "demo-deposit-1"));
    assertThat(publisher.accountUpdates()).contains(new AccountUpdated(1L, 500L, 0L));
  }

  @Test
  void depositsAccumulateOnTheSameAccount() {
    Ledger ledger = new Ledger();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);

    engine.processCommand(new DepositCash(1L, 500L, "ref-1"));
    engine.processCommand(new DepositCash(1L, 250L, "ref-2"));

    assertThat(ledger.cashOf(1L)).isEqualTo(750L);
    assertThat(publisher.events())
        .contains(new CashDeposited(1L, 250L, 750L, "ref-2"));
  }

  @Test
  void withdrawalDebitsWhenFundedAndEmitsEvents() {
    Ledger ledger = new Ledger();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);
    engine.processCommand(new DepositCash(1L, 500L, "deposit"));

    engine.processCommand(new WithdrawCash(7L, 1L, 200L, "demo-withdrawal-1"));

    assertThat(ledger.cashOf(1L)).isEqualTo(300L);
    assertThat(publisher.events())
        .contains(new CashWithdrawn(1L, 200L, 300L, "demo-withdrawal-1"));
    assertThat(publisher.accountUpdates()).contains(new AccountUpdated(1L, 300L, 0L));
  }

  @Test
  void withdrawalIsRejectedWhenUnderfundedAndDoesNotMutate() {
    Ledger ledger = new Ledger();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);
    engine.processCommand(new DepositCash(1L, 100L, "deposit"));

    engine.processCommand(new WithdrawCash(7L, 1L, 500L, "demo-withdrawal-1"));

    assertThat(ledger.cashOf(1L)).isEqualTo(100L);
    assertThat(publisher.events())
        .noneMatch(event -> event instanceof CashWithdrawn);
  }

  @Test
  void publicDepositAndWithdrawApplyThroughTheRunningEngine() throws InterruptedException {
    Ledger ledger = new Ledger();
    RecordingEventPublisher publisher = new RecordingEventPublisher();
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, publisher, ledger, ledger);
    engine.start();

    assertThat(engine.deposit(1L, 1_000L, "demo-deposit-1"))
        .isEqualTo(SubmitResult.ENQUEUED);
    WithdrawalOutcome applied =
        engine.withdraw(1L, 400L, "demo-withdrawal-1", Duration.ofSeconds(2));
    WithdrawalOutcome rejected =
        engine.withdraw(1L, 10_000L, "demo-withdrawal-2", Duration.ofSeconds(2));

    engine.stop();

    assertThat(applied).isEqualTo(WithdrawalOutcome.APPLIED);
    assertThat(rejected).isEqualTo(WithdrawalOutcome.INSUFFICIENT_FUNDS);
    assertThat(ledger.cashOf(1L)).isEqualTo(600L);
  }

  @Test
  void withdrawalOnStoppedEngineIsUnavailable() throws InterruptedException {
    Ledger ledger = new Ledger();
    MatchingEngine engine =
        new MatchingEngine(SYMBOL, 16, new RecordingEventPublisher(), ledger, ledger);
    engine.start();
    engine.stop();

    assertThat(engine.withdraw(1L, 100L, "demo-withdrawal-1", Duration.ofMillis(200)))
        .isEqualTo(WithdrawalOutcome.UNAVAILABLE);
  }
}
