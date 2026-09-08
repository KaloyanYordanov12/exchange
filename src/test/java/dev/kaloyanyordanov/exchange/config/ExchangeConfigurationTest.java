package dev.kaloyanyordanov.exchange.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.kaloyanyordanov.exchange.api.AdminAuthFilter;
import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.candle.CandleSeedRunner;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.SymbolProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedgerListener;
import dev.kaloyanyordanov.exchange.payment.DemoPaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.PairBroadcasters;
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

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ExchangeConfiguration configuration = new ExchangeConfiguration();

  private PairBroadcasters pairBroadcasters() {
    return configuration.pairBroadcasters(MAPPER, PROPERTIES);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<PersistenceWorker> noPersistence() {
    return mock(ObjectProvider.class);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CashLedgerListener> noListener() {
    return mock(ObjectProvider.class);
  }

  @Test
  void paymentProviderIsTheDemoProvider() {
    assertThat(configuration.paymentProvider()).isInstanceOf(DemoPaymentProvider.class);
  }

  @Test
  void buildsPaymentService() {
    CashLedger cash = configuration.cashLedger(PROPERTIES, noListener());
    PaymentService service = configuration.paymentService(new DemoPaymentProvider(), cash, 2000L);
    assertThat(service).isNotNull();
  }

  @Test
  void buildsRegistryWithTheFiveStandardPairs() {
    CashLedger cash = configuration.cashLedger(PROPERTIES, noListener());
    PaymentProvider provider = configuration.paymentProvider();
    PaymentService payment = configuration.paymentService(provider, cash, 2000L);
    ExchangeRegistry registry =
        configuration.exchangeRegistry(
            PROPERTIES, cash, payment, pairBroadcasters(), noPersistence(),
            configuration.candleStore(), 2000L, 1000L, 2000L);
    assertThat(registry.pairs()).hasSize(5);
    assertThat(registry.hasPair("BTC-USD")).isTrue();
    assertThat(registry.hasPair("DOGE-USD")).isTrue();
    assertThat(registry.hasPair("NOPE-USD")).isFalse();
  }

  @Test
  void buildsCandleServiceAndDisabledSeedRunnerByDefault() {
    assertThat(configuration.candleService(configuration.candleStore())).isNotNull();
    CandleSeedRunner runner =
        configuration.candleSeedRunner(PROPERTIES, configuration.candleStore(), false, 42L);
    assertThat(runner.enabled()).isFalse();
  }

  @Test
  void adminFilterIsScopedToAdminSurface() {
    FilterRegistrationBean<AdminAuthFilter> registration =
        configuration.adminAuthFilter("$2a$10$hash");
    assertThat(registration.getUrlPatterns()).containsExactly("/admin/*");
  }

  @Test
  void broadcastersAreBuiltOnePerPair() {
    PairBroadcasters broadcasters = pairBroadcasters();
    assertThat(broadcasters.all()).hasSize(5);
    assertThat(broadcasters.forPair("BTC-USD")).isNotNull();
    assertThat(broadcasters.forPair("DOGE-USD").pairId()).isEqualTo("DOGE-USD");
    assertThat(broadcasters.forPair("NOPE-USD")).isNull();
  }

  @Test
  void webSocketHandlerIsBuilt() {
    MarketDataWebSocketHandler handler =
        configuration.marketDataWebSocketHandler(pairBroadcasters(), PROPERTIES);
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
