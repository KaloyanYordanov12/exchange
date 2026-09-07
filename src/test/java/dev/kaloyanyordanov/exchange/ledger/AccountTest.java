package dev.kaloyanyordanov.exchange.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void exposesBalances() {
    Account account = new Account(7L, 500L, 30L);
    assertThat(account.id()).isEqualTo(7L);
    assertThat(account.cash()).isEqualTo(500L);
    assertThat(account.asset()).isEqualTo(30L);
  }

  @Test
  void zeroBalancesAllowed() {
    Account account = new Account(1L, 0L, 0L);
    assertThat(account.cash()).isZero();
    assertThat(account.asset()).isZero();
  }

  @Test
  void negativeCashRejected() {
    assertThatThrownBy(() -> new Account(1L, -1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeAssetRejected() {
    assertThatThrownBy(() -> new Account(1L, 0L, -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
