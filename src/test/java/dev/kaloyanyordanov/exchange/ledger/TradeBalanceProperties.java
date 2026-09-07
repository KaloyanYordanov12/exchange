package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Trade;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/**
 * Invariant 7 — every trade balances. Each settled fill moves exactly the trade
 * notional of cash and the trade quantity of asset between the two parties, in
 * equal and opposite directions, leaving system totals unchanged.
 */
class TradeBalanceProperties {

  private static final long BUYER = 0L;
  private static final long SELLER = 1L;

  @Property
  void settlementIsEqualAndOpposite(
      @ForAll @LongRange(min = 1L, max = 1_000L) long price,
      @ForAll @LongRange(min = 1L, max = 100L) long quantity,
      @ForAll @LongRange(min = 0L, max = 1_000L) long buyerExtraCash,
      @ForAll @LongRange(min = 0L, max = 500L) long buyerAsset,
      @ForAll @LongRange(min = 0L, max = 500L) long sellerCash,
      @ForAll @LongRange(min = 0L, max = 1_000L) long sellerExtraAsset) {
    long notional = price * quantity;
    Ledger ledger = new Ledger();
    ledger.deposit(BUYER, notional + buyerExtraCash, buyerAsset);
    ledger.deposit(SELLER, sellerCash, quantity + sellerExtraAsset);

    final long buyerCashBefore = ledger.cashOf(BUYER);
    final long buyerAssetBefore = ledger.assetOf(BUYER);
    final long sellerCashBefore = ledger.cashOf(SELLER);
    final long sellerAssetBefore = ledger.assetOf(SELLER);
    final long totalCashBefore = ledger.totalCash();
    final long totalAssetBefore = ledger.totalAsset();

    Trade trade =
        new Trade(OrderId.of(1L), OrderId.of(2L), price, quantity, BUYER, SELLER, 0L);
    ledger.onFill(trade);

    // Equal and opposite: cash and asset move by exactly notional and quantity.
    assertThat(ledger.cashOf(BUYER)).isEqualTo(buyerCashBefore - notional);
    assertThat(ledger.cashOf(SELLER)).isEqualTo(sellerCashBefore + notional);
    assertThat(ledger.assetOf(BUYER)).isEqualTo(buyerAssetBefore + quantity);
    assertThat(ledger.assetOf(SELLER)).isEqualTo(sellerAssetBefore - quantity);
    // Totals unchanged.
    assertThat(ledger.totalCash()).isEqualTo(totalCashBefore);
    assertThat(ledger.totalAsset()).isEqualTo(totalAssetBefore);
  }
}
