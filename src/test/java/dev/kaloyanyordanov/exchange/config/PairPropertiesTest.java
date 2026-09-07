package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import org.junit.jupiter.api.Test;

class PairPropertiesTest {

  @Test
  void buildsSymbolAndPairId() {
    PairProperties btc = new PairProperties("BTC", "USD", 10_000L, 1L, 40_000_000_000L);
    assertThat(btc.toSymbol()).isEqualTo(new Symbol("BTC", "USD", 10_000L, 1L));
    assertThat(btc.pairId()).isEqualTo("BTC-USD");
    assertThat(btc.toSymbol().pairId()).isEqualTo("BTC-USD");
  }

  @Test
  void scaledIntegerPriceMathIsExactAtBtcMagnitude() {
    // BTC ~ $40,000 in micro-USD: a big price, still exact in long arithmetic.
    Symbol btc = new PairProperties("BTC", "USD", 10_000L, 1L, 40_000_000_000L).toSymbol();
    assertThat(btc.isValidPrice(40_000_000_000L)).isTrue();
    assertThat(btc.isValidPrice(40_000_000_001L)).isFalse(); // not tick-aligned
    // A large notional (price x quantity) is exact, no float drift, no overflow.
    long notional = Math.multiplyExact(40_000_000_000L, 250_000L);
    assertThat(notional).isEqualTo(10_000_000_000_000_000L);
  }

  @Test
  void scaledIntegerPriceMathIsExactAtDogeMagnitude() {
    // DOGE ~ $0.08 in micro-USD: a tiny price, exact to the unit.
    Symbol doge = new PairProperties("DOGE", "USD", 1L, 1L, 80_000L).toSymbol();
    assertThat(doge.isValidPrice(80_000L)).isTrue();
    assertThat(doge.isValidPrice(79_999L)).isTrue(); // tick is 1, so every unit is valid
    long notional = Math.multiplyExact(80_000L, 1_000_000L);
    assertThat(notional).isEqualTo(80_000_000_000L);
  }

  @Test
  void standardPairsSpanFiveOrdersOfMagnitude() {
    assertThat(ExchangeProperties.STANDARD_PAIRS).hasSize(5);
    long btc = ExchangeProperties.STANDARD_PAIRS.get(0).referencePrice();
    long doge = ExchangeProperties.STANDARD_PAIRS.get(4).referencePrice();
    assertThat(btc).isEqualTo(40_000_000_000L);
    assertThat(doge).isEqualTo(80_000L);
    assertThat(btc / doge).isEqualTo(500_000L);
  }

  @Test
  void rejectsMisalignedReferencePrice() {
    assertThatThrownBy(() -> new PairProperties("BTC", "USD", 10_000L, 1L, 40_000_000_001L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tick-aligned");
  }

  @Test
  void rejectsInvalidScalingRules() {
    assertThatThrownBy(() -> new PairProperties("", "USD", 1L, 1L, 100L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PairProperties("BTC", "USD", 0L, 1L, 100L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PairProperties("BTC", "USD", 1L, 0L, 100L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PairProperties("BTC", "USD", 1L, 1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
