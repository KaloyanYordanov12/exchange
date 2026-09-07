package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.api.ApiKeyAuthFilter;
import dev.kaloyanyordanov.exchange.api.TraderRegistry;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.FanoutPublisher;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.ledger.Ledger;
import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import dev.kaloyanyordanov.exchange.realtime.ThrottledBroadcaster;
import java.util.List;
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
   * Fans engine events out to the read model and the broadcaster (the persistence
   * sink is added in Phase 6).
   *
   * @param marketDataCache the read model sink
   * @param broadcaster     the broadcast sink
   * @return the fan-out publisher
   */
  @Bean
  public FanoutPublisher fanoutPublisher(
      MarketDataCache marketDataCache, ThrottledBroadcaster broadcaster) {
    return new FanoutPublisher(List.<EventPublisher>of(marketDataCache, broadcaster));
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
