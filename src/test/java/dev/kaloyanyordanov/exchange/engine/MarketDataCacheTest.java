package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.ledger.Account;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataCacheTest {

  @Test
  void startsWithAnEmptyBookAndNoBalances() {
    MarketDataCache cache = new MarketDataCache();
    assertThat(cache.book().bids()).isEmpty();
    assertThat(cache.book().asks()).isEmpty();
    assertThat(cache.balanceOf(1L)).isEmpty();
  }

  @Test
  void bookChangedUpdatesLatestSnapshot() {
    MarketDataCache cache = new MarketDataCache();
    BookSnapshot snapshot =
        new BookSnapshot(List.of(new PriceLevel(100L, 5L)), List.of(new PriceLevel(110L, 3L)));
    cache.publish(new BookChanged(snapshot));
    assertThat(cache.book()).isEqualTo(snapshot);
  }

  @Test
  void accountUpdatedUpdatesBalance() {
    MarketDataCache cache = new MarketDataCache();
    cache.publish(new AccountUpdated(7L, 900L, 12L));
    assertThat(cache.balanceOf(7L)).contains(new Account(7L, 900L, 12L));
  }

  @Test
  void seedAccountSetsOpeningBalance() {
    MarketDataCache cache = new MarketDataCache();
    cache.seedAccount(3L, 1_000L, 50L);
    assertThat(cache.balanceOf(3L)).contains(new Account(3L, 1_000L, 50L));
  }

  @Test
  void ignoresEventsThatAreNotPartOfTheReadModel() {
    MarketDataCache cache = new MarketDataCache();
    cache.publish(new OrderAccepted(OrderId.of(1L), Side.BUY, 100L, 5L, 1L, 0L));
    cache.publish(
        new OrderRejected(OrderId.of(1L), OrderRejected.RejectReason.INSUFFICIENT_CASH, 1L));
    cache.publish(
        new TradeExecuted(new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 1L, 2L, 0L)));
    assertThat(cache.book().bids()).isEmpty();
    assertThat(cache.balanceOf(1L)).isEmpty();
  }
}
