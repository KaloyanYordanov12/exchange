package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.kaloyanyordanov.exchange.api.AdminAuthFilter;
import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.SymbolProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.engine.BookChanged;
import dev.kaloyanyordanov.exchange.engine.FanoutPublisher;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.ledger.Account;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.SimulatorMetricsSink;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.ObjectMapper;

class ExchangeConfigurationTest {

  private static final ExchangeProperties PROPERTIES =
      new ExchangeProperties(
          new SymbolProperties("BTC", "USD", 5L, 2L),
          1_000,
          List.of(new TraderProperties(1L, "hash", 500L, 20L)),
          null);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ExchangeConfiguration configuration = new ExchangeConfiguration();

  private ThrottledBroadcaster broadcaster() {
    return configuration.broadcaster(MAPPER, PROPERTIES);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<PersistenceWorker> noPersistence() {
    // A mocked provider's ifAvailable is a no-op, so no worker is added.
    return mock(ObjectProvider.class);
  }

  @Test
  void buildsSymbolFromProperties() {
    Symbol symbol = configuration.symbol(PROPERTIES);
    assertThat(symbol.base()).isEqualTo("BTC");
    assertThat(symbol.quote()).isEqualTo("USD");
    assertThat(symbol.tickSize()).isEqualTo(5L);
    assertThat(symbol.lotSize()).isEqualTo(2L);
  }

  @Test
  void fundsLedgerFromTraders() {
    Ledger ledger = configuration.ledger(PROPERTIES);
    assertThat(ledger.cashOf(1L)).isEqualTo(500L);
    assertThat(ledger.assetOf(1L)).isEqualTo(20L);
  }

  @Test
  void seedsReadModelFromTraders() {
    MarketDataCache cache = configuration.marketDataCache(PROPERTIES);
    assertThat(cache.balanceOf(1L)).contains(new Account(1L, 500L, 20L));
  }

  @Test
  void engineUsesConfiguredCapacity() {
    MatchingEngine engine =
        configuration.matchingEngine(
            configuration.symbol(PROPERTIES),
            PROPERTIES,
            configuration.fanoutPublisher(
                new MarketDataCache(), broadcaster(), new SimulatorMetricsSink(), noPersistence()),
            configuration.ledger(PROPERTIES));
    assertThat(engine.ingressCapacity()).isEqualTo(1024);
  }

  @Test
  void fanoutForwardsToTheReadModelAndBroadcaster() {
    MarketDataCache cache = new MarketDataCache();
    ThrottledBroadcaster broadcaster = broadcaster();
    FanoutPublisher fanout =
        configuration.fanoutPublisher(
            cache, broadcaster, new SimulatorMetricsSink(), noPersistence());
    BookSnapshot snapshot = new BookSnapshot(List.of(new PriceLevel(100L, 5L)), List.of());
    fanout.publish(new BookChanged(snapshot));
    assertThat(cache.book()).isEqualTo(snapshot);
  }

  @Test
  void adminFilterIsScopedToAdminSurface() {
    FilterRegistrationBean<AdminAuthFilter> registration =
        configuration.adminAuthFilter("$2a$10$hash");
    assertThat(registration.getUrlPatterns()).containsExactly("/admin/*");
  }

  @Test
  void loadSimulatorIsBuilt() {
    MatchingEngine engine =
        configuration.matchingEngine(
            configuration.symbol(PROPERTIES),
            PROPERTIES,
            configuration.fanoutPublisher(
                new MarketDataCache(), broadcaster(), new SimulatorMetricsSink(), noPersistence()),
            configuration.ledger(PROPERTIES));
    LoadSimulator simulator =
        configuration.loadSimulator(
            engine, configuration.symbol(PROPERTIES), new SimulatorMetricsSink());
    assertThat(simulator.isRunning()).isFalse();
  }

  @Test
  void broadcasterIsBuilt() {
    assertThat(broadcaster().clientCount()).isZero();
  }

  @Test
  void webSocketHandlerIsBuilt() {
    MarketDataWebSocketHandler handler =
        configuration.marketDataWebSocketHandler(broadcaster(), PROPERTIES);
    assertThat(handler.connectionCount()).isZero();
  }

  @Test
  void traderRegistryHoldsConfiguredTraders() {
    TraderRegistry registry = configuration.traderRegistry(PROPERTIES);
    assertThat(registry.traders()).hasSize(1);
  }

  @Test
  void authFilterIsScopedToOrdersAndAccounts() {
    FilterRegistrationBean<ApiKeyAuthFilter> registration =
        configuration.apiKeyAuthFilter(configuration.traderRegistry(PROPERTIES));
    assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/orders", "/accounts/*");
  }
}
