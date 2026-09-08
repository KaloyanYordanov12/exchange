package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.api.AdminAuthFilter;
import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.ExchangeService;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.FanoutPublisher;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.invariant.InvariantMonitor;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedgerListener;
import dev.kaloyanyordanov.exchange.payment.DemoPaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentProvider;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.SimulatorMetricsSink;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the engine, ledgers, read model, and API. The shared cash ledger and the
 * matching engine are started as bean lifecycles (the cash ledger before the engine,
 * which settles into it, and stopped in reverse so no settlement is lost), and the
 * asset ledger is endowed before its engine starts, so no request is served before
 * matching is live.
 */
@Configuration
@EnableConfigurationProperties(ExchangeProperties.class)
public class ExchangeConfiguration {

  /**
   * The traded symbol.
   *
   * @param properties the exchange configuration
   * @return the symbol
   */
  @Bean
  public Symbol symbol(ExchangeProperties properties) {
    ExchangeProperties.SymbolProperties symbol = properties.symbol();
    return new Symbol(symbol.base(), symbol.quote(), symbol.tickSize(), symbol.lotSize());
  }

  /**
   * The per-pair asset ledger, endowed with the configured opening asset. Cash is not
   * held here; it lives in the shared cash ledger.
   *
   * @param properties the exchange configuration
   * @return the asset-endowed ledger
   */
  @Bean
  public AssetLedger assetLedger(ExchangeProperties properties) {
    AssetLedger ledger = new AssetLedger();
    for (TraderProperties trader : properties.traders()) {
      ledger.endow(trader.accountId(), trader.asset());
    }
    return ledger;
  }

  /**
   * The shared cash ledger (single owner of every account's cash), started/stopped
   * with the context. Under the persistence profile a {@link CashLedgerListener}
   * audits its movements.
   *
   * @param properties the exchange configuration
   * @param listener   the optional cash-movement audit sink
   * @return the cash ledger
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public CashLedger cashLedger(
      ExchangeProperties properties, ObjectProvider<CashLedgerListener> listener) {
    return new CashLedger(properties.ingressCapacity(), listener.getIfAvailable());
  }

  /**
   * The per-pair read model, seeded with the configured opening asset. Cash arrives
   * from the cash ledger's snapshot when a balance is queried.
   *
   * @param properties the exchange configuration
   * @return the asset-seeded read model
   */
  @Bean
  public MarketDataCache marketDataCache(ExchangeProperties properties) {
    MarketDataCache cache = new MarketDataCache();
    for (TraderProperties trader : properties.traders()) {
      cache.seedAsset(trader.accountId(), trader.asset());
    }
    return cache;
  }

  /**
   * Funds the configured accounts' opening cash through the shared cash ledger once
   * it has started (replacing off-thread seeding).
   *
   * @param cashLedger the started cash ledger
   * @param properties the exchange configuration
   * @return the genesis funder
   */
  @Bean(initMethod = "fund")
  public GenesisFunder genesisFunder(CashLedger cashLedger, ExchangeProperties properties) {
    return new GenesisFunder(cashLedger, properties.traders(), Duration.ofSeconds(5));
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
   * Fans engine events out to the read model, the broadcaster, and (under the
   * {@code persistence} profile) the async persistence worker.
   *
   * @param marketDataCache      the read model sink
   * @param broadcaster          the broadcast sink
   * @param simulatorMetricsSink the simulator metrics sink
   * @param persistenceWorker    the optional persistence sink (present under the profile)
   * @return the fan-out publisher
   */
  @Bean
  public FanoutPublisher fanoutPublisher(
      MarketDataCache marketDataCache,
      ThrottledBroadcaster broadcaster,
      SimulatorMetricsSink simulatorMetricsSink,
      ObjectProvider<PersistenceWorker> persistenceWorker) {
    List<EventPublisher> sinks =
        new ArrayList<>(List.of(marketDataCache, broadcaster, simulatorMetricsSink));
    persistenceWorker.ifAvailable(sinks::add);
    return new FanoutPublisher(sinks);
  }

  /**
   * The simulator metrics sink (records processed events during a run).
   *
   * @return the metrics sink
   */
  @Bean
  public SimulatorMetricsSink simulatorMetricsSink() {
    return new SimulatorMetricsSink();
  }

  /**
   * The load simulator, driving the engine through its real ingress and reserving a
   * buy's cash in the shared cash ledger like the API.
   *
   * @param engine               the matching engine
   * @param symbol               the traded symbol
   * @param cashLedger           the shared cash ledger
   * @param simulatorMetricsSink the metrics sink
   * @return the load simulator
   */
  @Bean
  public LoadSimulator loadSimulator(
      MatchingEngine engine,
      Symbol symbol,
      CashLedger cashLedger,
      SimulatorMetricsSink simulatorMetricsSink) {
    return new LoadSimulator(engine, symbol, cashLedger, simulatorMetricsSink);
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
   * The matching engine, started/stopped with the application context.
   *
   * @param symbol      the traded symbol
   * @param properties  the exchange configuration
   * @param publisher   the outbound event sink
   * @param assetLedger the per-pair asset ledger and fill policy
   * @param cashLedger  the shared cash ledger for cash settlement
   * @return the matching engine
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public MatchingEngine matchingEngine(
      Symbol symbol,
      ExchangeProperties properties,
      FanoutPublisher publisher,
      AssetLedger assetLedger,
      CashLedger cashLedger) {
    return new MatchingEngine(
        symbol, properties.ingressCapacity(), publisher, assetLedger, cashLedger);
  }

  /**
   * The API gateway bean.
   *
   * @param engine         the matching engine
   * @param marketData     the read model
   * @param cashLedger     the shared cash ledger
   * @param paymentService the deposit/withdrawal orchestrator
   * @param symbol         the traded symbol
   * @param timeoutMillis  how long to wait for a reservation or balance snapshot
   * @return the exchange service
   */
  @Bean
  public ExchangeService exchangeService(
      MatchingEngine engine,
      MarketDataCache marketData,
      CashLedger cashLedger,
      PaymentService paymentService,
      Symbol symbol,
      @Value("${exchange.ledger.timeout-millis:2000}") long timeoutMillis) {
    return new ExchangeService(
        engine,
        marketData,
        cashLedger,
        paymentService,
        symbol,
        Duration.ofMillis(timeoutMillis),
        0L);
  }

  /**
   * The invariant monitor, checking the per-pair and cash invariants continuously.
   *
   * @param engine         the matching engine
   * @param cashLedger     the shared cash ledger
   * @param intervalMillis the continuous check interval
   * @param timeoutMillis  the per-check snapshot timeout
   * @return the invariant monitor
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public InvariantMonitor invariantMonitor(
      MatchingEngine engine,
      CashLedger cashLedger,
      @Value("${exchange.invariant.check-interval-millis:1000}") long intervalMillis,
      @Value("${exchange.invariant.snapshot-timeout-millis:2000}") long timeoutMillis) {
    return new InvariantMonitor(engine, cashLedger, intervalMillis, timeoutMillis);
  }

  /**
   * The payment provider. Fail-secure: the demo provider is used unless a real one is
   * explicitly configured as a bean (and none exists), so there is no path to real
   * money.
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
   * book endpoint is public market data.
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
