package dev.kaloyanyordanov.exchange.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.Command;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import dev.kaloyanyordanov.exchange.ledger.Account;
import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashSnapshot;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import dev.kaloyanyordanov.exchange.payment.FundingResult;
import dev.kaloyanyordanov.exchange.payment.FundingStatus;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExchangeServiceTest {

  // tick 5, lot 2 so alignment validation is meaningful.
  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 5L, 2L);

  private final MatchingEngine engine = mock(MatchingEngine.class);
  private final MarketDataCache cache = new MarketDataCache();
  private final CashLedger cashLedger = mock(CashLedger.class);
  private final PaymentService paymentService = mock(PaymentService.class);
  private final ExchangeService service =
      new ExchangeService(
          engine, cache, cashLedger, paymentService, SYMBOL, Duration.ofSeconds(2), 0L);

  private void reservationSucceeds() {
    when(cashLedger.reserve(anyLong(), anyLong(), any())).thenReturn(ReservationOutcome.RESERVED);
  }

  @Test
  void validOrderIsEnqueuedAndAssignedAnIncrementingId() {
    reservationSucceeds();
    when(engine.submit(any())).thenReturn(SubmitResult.ENQUEUED);

    PlacementOutcome first = service.place(1L, Side.BUY, 10L, 4L);
    PlacementOutcome second = service.place(1L, Side.SELL, 15L, 2L);

    assertThat(first.status()).isEqualTo(PlacementOutcome.Status.ACCEPTED);
    assertThat(first.orderId()).isZero();
    assertThat(second.orderId()).isEqualTo(1L);
    verify(engine, times(2)).submit(any(Command.class));
  }

  @Test
  void misalignedPriceIsInvalidAndNeverSubmitted() {
    PlacementOutcome outcome = service.place(1L, Side.BUY, 7L, 4L);
    assertThat(outcome.status()).isEqualTo(PlacementOutcome.Status.INVALID);
    verify(engine, never()).submit(any());
  }

  @Test
  void misalignedQuantityIsInvalidAndNeverSubmitted() {
    PlacementOutcome outcome = service.place(1L, Side.BUY, 10L, 3L);
    assertThat(outcome.status()).isEqualTo(PlacementOutcome.Status.INVALID);
    verify(engine, never()).submit(any());
  }

  @Test
  void nonPositivePriceIsInvalid() {
    assertThat(service.place(1L, Side.BUY, 0L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.INVALID);
  }

  @Test
  void insufficientCashRejectsBuyBeforeSubmit() {
    when(cashLedger.reserve(anyLong(), anyLong(), any()))
        .thenReturn(ReservationOutcome.INSUFFICIENT_FUNDS);
    assertThat(service.place(1L, Side.BUY, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.REJECTED);
    verify(engine, never()).submit(any());
  }

  @Test
  void fullQueueSurfacesBusyAndReleasesReservation() {
    reservationSucceeds();
    when(engine.submit(any())).thenReturn(SubmitResult.REJECTED_BUSY);
    assertThat(service.place(1L, Side.BUY, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.BUSY);
    verify(cashLedger).release(1L, 40L); // reserved 10 x 4, released on submit failure
  }

  @Test
  void notRunningSurfacesBusy() {
    reservationSucceeds();
    when(engine.submit(any())).thenReturn(SubmitResult.REJECTED_NOT_RUNNING);
    assertThat(service.place(1L, Side.BUY, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.BUSY);
  }

  @Test
  void sellOrderDoesNotReserveCash() {
    when(engine.submit(any())).thenReturn(SubmitResult.ENQUEUED);
    assertThat(service.place(1L, Side.SELL, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.ACCEPTED);
    verify(cashLedger, never()).reserve(anyLong(), anyLong(), any());
  }

  @Test
  void balanceCombinesLedgerCashAndPairAsset() {
    cache.seedAsset(9L, 20L);
    when(cashLedger.snapshot(any()))
        .thenReturn(Optional.of(new CashSnapshot(List.of(new CashAccount(9L, 500L, 30L)), 0L, 0L)));
    // Cash shown is available + reserved = 530; asset from this pair is 20.
    assertThat(service.balance(9L)).isEqualTo(new Account(9L, 530L, 20L));
    assertThat(service.book().bids()).isEmpty();
  }

  @Test
  void depositDelegatesToThePaymentService() {
    FundingResult expected = new FundingResult(FundingStatus.ACCEPTED, "demo-deposit-1");
    when(paymentService.deposit(9L, 500L)).thenReturn(expected);
    assertThat(service.deposit(9L, 500L)).isSameAs(expected);
    verify(paymentService).deposit(9L, 500L);
  }

  @Test
  void withdrawDelegatesToThePaymentService() {
    FundingResult expected = new FundingResult(FundingStatus.APPLIED, "demo-withdrawal-1");
    when(paymentService.withdraw(9L, 200L)).thenReturn(expected);
    assertThat(service.withdraw(9L, 200L)).isSameAs(expected);
    verify(paymentService).withdraw(9L, 200L);
  }
}
