package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataCacheTest {

  @Test
  void startsWithAnEmptyBookAndNoAssets() {
    MarketDataCache cache = new MarketDataCache();
    assertThat(cache.book().bids()).isEmpty();
    assertThat(cache.book().asks()).isEmpty();
    assertThat(cache.assetOf(1L)).isEmpty();
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
  void accountUpdatedUpdatesAsset() {
    MarketDataCache cache = new MarketDataCache();
    cache.publish(new AccountUpdated(7L, 12L));
    assertThat(cache.assetOf(7L)).contains(12L);
  }

  @Test
  void seedAssetSetsOpeningAsset() {
    MarketDataCache cache = new MarketDataCache();
    cache.seedAsset(3L, 50L);
    assertThat(cache.assetOf(3L)).contains(50L);
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
    assertThat(cache.assetOf(1L)).isEmpty();
  }
}
