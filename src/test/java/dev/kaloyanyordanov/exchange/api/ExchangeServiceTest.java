package dev.kaloyanyordanov.exchange.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.Test;

class ExchangeServiceTest {

  // tick 5, lot 2 so alignment validation is meaningful.
  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 5L, 2L);

  private final MatchingEngine engine = mock(MatchingEngine.class);
  private final MarketDataCache cache = new MarketDataCache();
  private final ExchangeService service = new ExchangeService(engine, cache, SYMBOL);

  @Test
  void validOrderIsEnqueuedAndAssignedAnIncrementingId() {
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
  void fullQueueSurfacesBusy() {
    when(engine.submit(any())).thenReturn(SubmitResult.REJECTED_BUSY);
    assertThat(service.place(1L, Side.BUY, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.BUSY);
  }

  @Test
  void notRunningSurfacesBusy() {
    when(engine.submit(any())).thenReturn(SubmitResult.REJECTED_NOT_RUNNING);
    assertThat(service.place(1L, Side.BUY, 10L, 4L).status())
        .isEqualTo(PlacementOutcome.Status.BUSY);
  }

  @Test
  void readsDelegateToTheCache() {
    cache.seedAccount(9L, 500L, 20L);
    assertThat(service.balance(9L)).contains(new Account(9L, 500L, 20L));
    assertThat(service.book().bids()).isEmpty();
  }
}
