package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SideTest {

  @Test
  void oppositeOfBuyIsSell() {
    assertThat(Side.BUY.opposite()).isEqualTo(Side.SELL);
  }

  @Test
  void oppositeOfSellIsBuy() {
    assertThat(Side.SELL.opposite()).isEqualTo(Side.BUY);
  }
}
