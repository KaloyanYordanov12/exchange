package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.SymbolProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExchangePropertiesTest {

  private static final SymbolProperties SYMBOL = new SymbolProperties("BTC", "USD", 1L, 1L);

  @Test
  void exposesConfiguredValues() {
    TraderProperties trader = new TraderProperties(1L, "$2a$hash", 1_000L, 50L);
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, 4096, List.of(trader));
    assertThat(properties.symbol()).isEqualTo(SYMBOL);
    assertThat(properties.ingressCapacity()).isEqualTo(4096);
    assertThat(properties.traders()).containsExactly(trader);
    assertThat(trader.accountId()).isEqualTo(1L);
    assertThat(trader.apiKeyHash()).isEqualTo("$2a$hash");
    assertThat(trader.cash()).isEqualTo(1_000L);
    assertThat(trader.asset()).isEqualTo(50L);
  }

  @Test
  void nullTradersBecomesEmpty() {
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, 16, null);
    assertThat(properties.traders()).isEmpty();
  }

  @Test
  void traderListIsDefensivelyCopied() {
    List<TraderProperties> mutable = new ArrayList<>();
    mutable.add(new TraderProperties(1L, "h", 0L, 0L));
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, 16, mutable);
    mutable.clear();
    assertThat(properties.traders()).hasSize(1);
    assertThatThrownBy(() -> properties.traders().add(new TraderProperties(2L, "h", 0L, 0L)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
