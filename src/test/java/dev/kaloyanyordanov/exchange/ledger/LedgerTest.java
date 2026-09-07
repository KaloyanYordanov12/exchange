package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Trade;
import org.junit.jupiter.api.Test;

class LedgerTest {

  private static Trade trade(long price, long qty, long buyer, long seller) {
    return new Trade(OrderId.of(1L), OrderId.of(2L), price, qty, buyer, seller, 0L);
  }

  @Test
  void depositAccumulates() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 100L, 5L);
    ledger.deposit(1L, 50L, 2L);
    assertThat(ledger.cashOf(1L)).isEqualTo(150L);
    assertThat(ledger.assetOf(1L)).isEqualTo(7L);
    assertThat(ledger.account(1L)).isEqualTo(new Account(1L, 150L, 7L));
  }

  @Test
  void unknownAccountIsZero() {
    Ledger ledger = new Ledger();
    assertThat(ledger.cashOf(99L)).isZero();
    assertThat(ledger.assetOf(99L)).isZero();
  }

  @Test
  void negativeDepositRejected() {
    Ledger ledger = new Ledger();
    assertThatThrownBy(() -> ledger.deposit(1L, -1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ledger.deposit(1L, 0L, -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accountIdsAreAnImmutableCopy() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 10L, 0L);
    ledger.deposit(2L, 10L, 0L);
    assertThat(ledger.accountIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThatThrownBy(() -> ledger.accountIds().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void totalsSumAcrossAccounts() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 100L, 5L);
    ledger.deposit(2L, 250L, 8L);
    assertThat(ledger.totalCash()).isEqualTo(350L);
    assertThat(ledger.totalAsset()).isEqualTo(13L);
  }

  @Test
  void maxBuyerUnitsIsFloorOfCashOverPrice() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 105L, 0L);
    assertThat(ledger.maxBuyerUnits(1L, 10L)).isEqualTo(10L);
    assertThat(ledger.maxBuyerUnits(1L, 106L)).isZero();
    assertThat(ledger.maxBuyerUnits(99L, 10L)).isZero();
  }

  @Test
  void maxBuyerUnitsRejectsNonPositivePrice() {
    assertThatThrownBy(() -> new Ledger().maxBuyerUnits(1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void maxSellerUnitsIsAssetBalance() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 0L, 42L);
    assertThat(ledger.maxSellerUnits(1L)).isEqualTo(42L);
    assertThat(ledger.maxSellerUnits(99L)).isZero();
  }

  @Test
  void onFillIsEqualAndOppositeAndConserves() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 1_000L, 0L); // buyer: cash only
    ledger.deposit(2L, 0L, 50L); // seller: asset only
    final long cashBefore = ledger.totalCash();
    final long assetBefore = ledger.totalAsset();

    ledger.onFill(trade(100L, 3L, 1L, 2L)); // notional 300

    assertThat(ledger.cashOf(1L)).isEqualTo(700L);
    assertThat(ledger.assetOf(1L)).isEqualTo(3L);
    assertThat(ledger.cashOf(2L)).isEqualTo(300L);
    assertThat(ledger.assetOf(2L)).isEqualTo(47L);
    // Conservation: totals unchanged by settlement.
    assertThat(ledger.totalCash()).isEqualTo(cashBefore);
    assertThat(ledger.totalAsset()).isEqualTo(assetBefore);
  }

  @Test
  void selfTradeNetsToZero() {
    Ledger ledger = new Ledger();
    ledger.deposit(1L, 1_000L, 50L);
    ledger.onFill(trade(100L, 4L, 1L, 1L)); // buyer == seller
    assertThat(ledger.cashOf(1L)).isEqualTo(1_000L);
    assertThat(ledger.assetOf(1L)).isEqualTo(50L);
  }
}
