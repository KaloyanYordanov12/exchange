package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.api.AdminAuthFilter;
import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.FanoutPublisher;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.invariant.InvariantMonitor;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import dev.kaloyanyordanov.exchange.persistence.PersistenceWorker;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.SimulatorMetricsSink;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the engine, ledger, read model, and API. The matching engine is started
 * as a bean lifecycle (after the ledger is funded and the read model seeded) and
 * stopped on shutdown, so no request is served before matching is live.
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
   * The ledger, funded from the configured trader opening balances.
   *
   * @param properties the exchange configuration
   * @return the funded ledger
   */
  @Bean
  public Ledger ledger(ExchangeProperties properties) {
    Ledger ledger = new Ledger();
    for (TraderProperties trader : properties.traders()) {
      ledger.deposit(trader.accountId(), trader.cash(), trader.asset());
    }
    return ledger;
  }

  /**
   * The read model, seeded with the configured opening balances.
   *
   * @param properties the exchange configuration
   * @return the seeded read model
   */
  @Bean
  public MarketDataCache marketDataCache(ExchangeProperties properties) {
    MarketDataCache cache = new MarketDataCache();
    for (TraderProperties trader : properties.traders()) {
      cache.seedAccount(trader.accountId(), trader.cash(), trader.asset());
    }
    return cache;
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
   * Fans engine events out to the read model, the broadcaster, and — when the
   * {@code persistence} profile is active — the async persistence worker.
   *
   * @param marketDataCache   the read model sink
   * @param broadcaster       the broadcast sink
   * @param persistenceWorker the optional persistence sink (present under the profile)
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
   * The load simulator, driving the engine through its real ingress.
   *
   * @param engine              the matching engine
   * @param symbol              the traded symbol
   * @param simulatorMetricsSink the metrics sink
   * @return the load simulator
   */
  @Bean
  public LoadSimulator loadSimulator(
      MatchingEngine engine, Symbol symbol, SimulatorMetricsSink simulatorMetricsSink) {
    return new LoadSimulator(engine, symbol, simulatorMetricsSink);
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
   * @param symbol     the traded symbol
   * @param properties the exchange configuration
   * @param publisher  the outbound event sink
   * @param ledger     the ledger (fill policy and account view)
   * @return the matching engine
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public MatchingEngine matchingEngine(
      Symbol symbol, ExchangeProperties properties, FanoutPublisher publisher, Ledger ledger) {
    return new MatchingEngine(symbol, properties.ingressCapacity(), publisher, ledger, ledger);
  }

  /**
   * The invariant monitor, checking the seven invariants continuously.
   *
   * @param engine        the matching engine
   * @param intervalMillis the continuous check interval
   * @param timeoutMillis  the per-check snapshot timeout
   * @return the invariant monitor
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public InvariantMonitor invariantMonitor(
      MatchingEngine engine,
      @Value("${exchange.invariant.check-interval-millis:1000}") long intervalMillis,
      @Value("${exchange.invariant.snapshot-timeout-millis:2000}") long timeoutMillis) {
    return new InvariantMonitor(engine, intervalMillis, timeoutMillis);
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
   * Registers the API-key auth filter on the order and account endpoints only;
   * the book endpoint is public market data.
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
