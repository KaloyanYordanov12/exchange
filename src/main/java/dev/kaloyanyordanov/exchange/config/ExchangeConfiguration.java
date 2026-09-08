package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.api.AdminAuthFilter;
import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedgerListener;
import dev.kaloyanyordanov.exchange.payment.DemoPaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the multi-pair platform: the shared cash ledger, the payment provider and
 * service, the real-time broadcaster, and the {@link ExchangeRegistry} that owns one
 * engine per pair and routes by pair id. The registry is the lifecycle owner (it
 * starts the cash ledger, funds genesis cash, and starts every engine and monitor),
 * so no request is served before matching is live.
 */
@Configuration
@EnableConfigurationProperties(ExchangeProperties.class)
public class ExchangeConfiguration {

  /**
   * The shared cash ledger (single owner of every account's cash). Started/stopped by
   * the {@link ExchangeRegistry}, not as a bean lifecycle. Under the persistence
   * profile a {@link CashLedgerListener} audits its movements.
   *
   * @param properties the exchange configuration
   * @param listener   the optional cash-movement audit sink
   * @return the cash ledger
   */
  @Bean
  public CashLedger cashLedger(
      ExchangeProperties properties, ObjectProvider<CashLedgerListener> listener) {
    return new CashLedger(properties.ingressCapacity(), listener.getIfAvailable());
  }

  /**
   * The throttling broadcaster, started/stopped with the application context.
   *
   * @param mapper     the JSON serializer source
   * @param properties the exchange configuration
   * @return the broadcaster
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public ThrottledBroadcaster broadcaster(ObjectMapper mapper, ExchangeProperties properties) {
    ExchangeProperties.BroadcastProperties broadcast = properties.broadcast();
    return new ThrottledBroadcaster(
        mapper::writeValueAsString,
        broadcast.bookHertz(),
        broadcast.maxTradesPerFlush(),
        broadcast.tapeCapacity());
  }

  /**
   * The payment provider. Fail-secure: the demo provider is used unless a real one is
   * explicitly configured (and none exists), so there is no path to real money.
   *
   * @return the payment provider
   */
  @Bean
  @ConditionalOnMissingBean(PaymentProvider.class)
  public PaymentProvider paymentProvider() {
    return new DemoPaymentProvider();
  }

  /**
   * The payment service orchestrating deposits and withdrawals across the provider
   * and the shared cash ledger.
   *
   * @param provider                the payment provider
   * @param cashLedger              the shared cash ledger
   * @param withdrawalTimeoutMillis how long to wait for a withdrawal outcome
   * @return the payment service
   */
  @Bean
  public PaymentService paymentService(
      PaymentProvider provider,
      CashLedger cashLedger,
      @Value("${exchange.payment.withdrawal-timeout-millis:2000}") long withdrawalTimeoutMillis) {
    return new PaymentService(provider, cashLedger, Duration.ofMillis(withdrawalTimeoutMillis));
  }

  /**
   * The exchange registry: one engine per configured pair, routed by pair id. It
   * starts the cash ledger and every engine and monitor on context start, and stops
   * them in reverse on shutdown.
   *
   * @param properties     the exchange configuration
   * @param cashLedger     the shared cash ledger
   * @param paymentService the deposit/withdrawal orchestrator
   * @param broadcaster    the shared real-time broadcast sink
   * @param persistence    the optional shared persistence sink
   * @param ledgerTimeout  the reservation/balance snapshot timeout in millis
   * @param intervalMillis the invariant check interval
   * @param timeoutMillis  the invariant snapshot timeout
   * @return the registry
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public ExchangeRegistry exchangeRegistry(
      ExchangeProperties properties,
      CashLedger cashLedger,
      PaymentService paymentService,
      ThrottledBroadcaster broadcaster,
      ObjectProvider<PersistenceWorker> persistence,
      @Value("${exchange.ledger.timeout-millis:2000}") long ledgerTimeout,
      @Value("${exchange.invariant.check-interval-millis:1000}") long intervalMillis,
      @Value("${exchange.invariant.snapshot-timeout-millis:2000}") long timeoutMillis) {
    EventPublisher persistenceSink = persistence.getIfAvailable();
    return new ExchangeRegistry(
        properties.pairs(),
        properties.ingressCapacity(),
        cashLedger,
        paymentService,
        broadcaster,
        persistenceSink,
        properties.traders(),
        Duration.ofMillis(ledgerTimeout),
        intervalMillis,
        timeoutMillis);
  }

  /**
   * Registers the admin auth filter on the admin surface.
   *
   * @param adminKeyHash the bcrypt hash of the admin key
   * @return the filter registration
   */
  @Bean
  public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(
      @Value("${exchange.admin.api-key-hash:}") String adminKeyHash) {
    FilterRegistrationBean<AdminAuthFilter> registration =
        new FilterRegistrationBean<>(new AdminAuthFilter(adminKeyHash));
    registration.addUrlPatterns("/admin/*");
    registration.setName("adminAuthFilter");
    return registration;
  }

  /**
   * The market-data WebSocket handler.
   *
   * @param broadcaster the broadcaster
   * @param properties  the exchange configuration
   * @return the handler
   */
  @Bean
  public MarketDataWebSocketHandler marketDataWebSocketHandler(
      ThrottledBroadcaster broadcaster, ExchangeProperties properties) {
    return new MarketDataWebSocketHandler(
        broadcaster, properties.broadcast().clientBufferSize());
  }

  /**
   * The trader registry resolving API keys to accounts.
   *
   * @param properties the exchange configuration
   * @return the trader registry
   */
  @Bean
  public TraderRegistry traderRegistry(ExchangeProperties properties) {
    return new TraderRegistry(properties.traders());
  }

  /**
   * Registers the API-key auth filter on the order and account endpoints only; the
   * book and pairs endpoints are public market data.
   *
   * @param registry the trader registry
   * @return the filter registration
   */
  @Bean
  public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(TraderRegistry registry) {
    FilterRegistrationBean<ApiKeyAuthFilter> registration =
        new FilterRegistrationBean<>(new ApiKeyAuthFilter(registry));
    registration.addUrlPatterns("/orders", "/accounts/*");
    registration.setName("apiKeyAuthFilter");
    return registration;
  }
}
