package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Trade;
import org.junit.jupiter.api.Test;

class AssetLedgerTest {

  private static Trade trade(long buyer, long seller, long price, long quantity) {
    return new Trade(OrderId.of(1L), OrderId.of(2L), price, quantity, buyer, seller, 0L);
  }

  @Test
  void endowsAndReportsAsset() {
    AssetLedger ledger = new AssetLedger();
    ledger.endow(1L, 100L);
    ledger.endow(1L, 50L);
    ledger.endow(2L, 20L);
    assertThat(ledger.assetOf(1L)).isEqualTo(150L);
    assertThat(ledger.assetOf(2L)).isEqualTo(20L);
    assertThat(ledger.assetOf(3L)).isZero();
    assertThat(ledger.totalAsset()).isEqualTo(170L);
    assertThat(ledger.accountIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void buyerIsUnconstrainedSellerIsCappedByAsset() {
    AssetLedger ledger = new AssetLedger();
    ledger.endow(2L, 7L);
    assertThat(ledger.maxBuyerUnits(1L, 100L)).isEqualTo(Long.MAX_VALUE);
    assertThat(ledger.maxSellerUnits(2L)).isEqualTo(7L);
    assertThat(ledger.maxSellerUnits(3L)).isZero();
  }

  @Test
  void onFillMovesAssetAndConservesTotal() {
    AssetLedger ledger = new AssetLedger();
    ledger.endow(2L, 10L); // seller holds 10
    ledger.onFill(trade(1L, 2L, 100L, 4L));
    assertThat(ledger.assetOf(1L)).isEqualTo(4L); // buyer received
    assertThat(ledger.assetOf(2L)).isEqualTo(6L); // seller delivered
    assertThat(ledger.totalAsset()).isEqualTo(10L); // conserved
  }

  @Test
  void rejectsNegativeEndowmentAndNonPositivePrice() {
    AssetLedger ledger = new AssetLedger();
    assertThatThrownBy(() -> ledger.endow(1L, -1L)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ledger.maxBuyerUnits(1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
