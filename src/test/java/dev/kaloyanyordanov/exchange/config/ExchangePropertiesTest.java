package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.BroadcastProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.SymbolProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExchangePropertiesTest {

  private static final SymbolProperties SYMBOL = new SymbolProperties("BTC", "USD", 1L, 1L);
  private static final BroadcastProperties BROADCAST =
      new BroadcastProperties(20, 100, 512, 64);

  @Test
  void exposesConfiguredValues() {
    TraderProperties trader = new TraderProperties(1L, "$2a$hash", 1_000L, 50L);
    ExchangeProperties properties =
        new ExchangeProperties(SYMBOL, null, 4096, List.of(trader), BROADCAST);
    assertThat(properties.symbol()).isEqualTo(SYMBOL);
    assertThat(properties.ingressCapacity()).isEqualTo(4096);
    assertThat(properties.traders()).containsExactly(trader);
    assertThat(properties.broadcast()).isEqualTo(BROADCAST);
    assertThat(trader.accountId()).isEqualTo(1L);
    assertThat(trader.apiKeyHash()).isEqualTo("$2a$hash");
    assertThat(trader.cash()).isEqualTo(1_000L);
    assertThat(trader.asset()).isEqualTo(50L);
  }

  @Test
  void nullTradersBecomesEmpty() {
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, null, 16, null, BROADCAST);
    assertThat(properties.traders()).isEmpty();
  }

  @Test
  void nullPairsBecomesTheFiveStandardPairs() {
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, null, 16, List.of(), BROADCAST);
    assertThat(properties.pairs()).isEqualTo(ExchangeProperties.STANDARD_PAIRS);
    assertThat(properties.pairs()).hasSize(5);
    assertThat(properties.pairs().stream().map(ExchangeProperties.PairProperties::pairId))
        .containsExactly("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD", "DOGE-USD");
  }

  @Test
  void nullBroadcastBecomesDefaults() {
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, null, 16, List.of(), null);
    assertThat(properties.broadcast().bookHertz()).isEqualTo(10);
    assertThat(properties.broadcast().maxTradesPerFlush()).isEqualTo(256);
    assertThat(properties.broadcast().tapeCapacity()).isEqualTo(1024);
    assertThat(properties.broadcast().clientBufferSize()).isEqualTo(256);
  }

  @Test
  void broadcastDefaultsReplaceNonPositiveValues() {
    BroadcastProperties defaulted = new BroadcastProperties(0, -1, 0, 0);
    assertThat(defaulted.bookHertz()).isEqualTo(10);
    assertThat(defaulted.maxTradesPerFlush()).isEqualTo(256);
    assertThat(defaulted.tapeCapacity()).isEqualTo(1024);
    assertThat(defaulted.clientBufferSize()).isEqualTo(256);
  }

  @Test
  void traderListIsDefensivelyCopied() {
    List<TraderProperties> mutable = new ArrayList<>();
    mutable.add(new TraderProperties(1L, "h", 0L, 0L));
    ExchangeProperties properties = new ExchangeProperties(SYMBOL, null, 16, mutable, BROADCAST);
    mutable.clear();
    assertThat(properties.traders()).hasSize(1);
    assertThatThrownBy(() -> properties.traders().add(new TraderProperties(2L, "h", 0L, 0L)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
