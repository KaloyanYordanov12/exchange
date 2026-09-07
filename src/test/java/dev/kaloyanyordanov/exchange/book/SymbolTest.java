package dev.kaloyanyordanov.exchange.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SymbolTest {

  private static final Symbol BTC_USD = new Symbol("BTC", "USD", 5L, 2L);

  @Test
  void exposesScalingRules() {
    assertThat(BTC_USD.base()).isEqualTo("BTC");
    assertThat(BTC_USD.quote()).isEqualTo("USD");
    assertThat(BTC_USD.tickSize()).isEqualTo(5L);
    assertThat(BTC_USD.lotSize()).isEqualTo(2L);
  }

  @Test
  void priceMustBePositiveTickMultiple() {
    assertThat(BTC_USD.isValidPrice(10L)).isTrue();
    assertThat(BTC_USD.isValidPrice(5L)).isTrue();
    assertThat(BTC_USD.isValidPrice(7L)).isFalse();
    assertThat(BTC_USD.isValidPrice(0L)).isFalse();
    assertThat(BTC_USD.isValidPrice(-5L)).isFalse();
  }

  @Test
  void quantityMustBePositiveLotMultiple() {
    assertThat(BTC_USD.isValidQuantity(4L)).isTrue();
    assertThat(BTC_USD.isValidQuantity(2L)).isTrue();
    assertThat(BTC_USD.isValidQuantity(3L)).isFalse();
    assertThat(BTC_USD.isValidQuantity(0L)).isFalse();
    assertThat(BTC_USD.isValidQuantity(-2L)).isFalse();
  }

  @Test
  void blankBaseRejected() {
    assertThatThrownBy(() -> new Symbol(" ", "USD", 1L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullBaseRejected() {
    assertThatThrownBy(() -> new Symbol(null, "USD", 1L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankQuoteRejected() {
    assertThatThrownBy(() -> new Symbol("BTC", "", 1L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullQuoteRejected() {
    assertThatThrownBy(() -> new Symbol("BTC", null, 1L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPositiveTickRejected() {
    assertThatThrownBy(() -> new Symbol("BTC", "USD", 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nonPositiveLotRejected() {
    assertThatThrownBy(() -> new Symbol("BTC", "USD", 1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
