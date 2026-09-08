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
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedgerListener;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.SimulatorMetricsSink;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.ObjectMapper;

class ExchangeConfigurationTest {

  private static final ExchangeProperties PROPERTIES =
      new ExchangeProperties(
          new SymbolProperties("BTC", "USD", 5L, 2L),
          null,
          1_000,
          List.of(new TraderProperties(1L, "hash", 500L, 20L)),
          null);
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ExchangeConfiguration configuration = new ExchangeConfiguration();

  private ThrottledBroadcaster broadcaster() {
    return configuration.broadcaster(MAPPER, PROPERTIES);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<PersistenceWorker> noPersistence() {
    return mock(ObjectProvider.class);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CashLedgerListener> noListener() {
    return mock(ObjectProvider.class);
  }

  private FanoutPublisher publisher(MarketDataCache cache) {
    return configuration.fanoutPublisher(
        cache, broadcaster(), new SimulatorMetricsSink(), noPersistence());
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
  void endowsAssetLedgerFromTraders() {
    AssetLedger ledger = configuration.assetLedger(PROPERTIES);
    assertThat(ledger.assetOf(1L)).isEqualTo(20L);
  }

  @Test
  void seedsReadModelAssetFromTraders() {
    MarketDataCache cache = configuration.marketDataCache(PROPERTIES);
    assertThat(cache.assetOf(1L)).contains(20L);
  }

  @Test
  void genesisFunderDepositsOpeningCashThroughTheCashLedger() throws InterruptedException {
    CashLedger cash = configuration.cashLedger(PROPERTIES, noListener());
    cash.start();
    try {
      configuration.genesisFunder(cash, PROPERTIES).fund();
      long available =
          cash.snapshot(TIMEOUT).orElseThrow().accounts().stream()
              .filter(account -> account.accountId() == 1L)
              .findFirst()
              .map(CashAccount::available)
              .orElse(0L);
      assertThat(available).isEqualTo(500L);
    } finally {
      cash.stop();
    }
  }

  @Test
  void engineUsesConfiguredCapacity() throws InterruptedException {
    CashLedger cash = configuration.cashLedger(PROPERTIES, noListener());
    MatchingEngine engine =
        configuration.matchingEngine(
            configuration.symbol(PROPERTIES),
            PROPERTIES,
            publisher(new MarketDataCache()),
            configuration.assetLedger(PROPERTIES),
            cash);
    assertThat(engine.ingressCapacity()).isEqualTo(1024);
  }

  @Test
  void fanoutForwardsToTheReadModelAndBroadcaster() {
    MarketDataCache cache = new MarketDataCache();
    FanoutPublisher fanout = publisher(cache);
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
    CashLedger cash = configuration.cashLedger(PROPERTIES, noListener());
    MatchingEngine engine =
        configuration.matchingEngine(
            configuration.symbol(PROPERTIES),
            PROPERTIES,
            publisher(new MarketDataCache()),
            configuration.assetLedger(PROPERTIES),
            cash);
    LoadSimulator simulator =
        configuration.loadSimulator(
            engine, configuration.symbol(PROPERTIES), cash, new SimulatorMetricsSink());
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
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  void authFilterIsScopedToOrdersAndAccounts() {
    FilterRegistrationBean<ApiKeyAuthFilter> registration =
        configuration.apiKeyAuthFilter(configuration.traderRegistry(PROPERTIES));
    assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder("/orders", "/accounts/*");
  }
}
