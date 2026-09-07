package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.realtime.MarketDataWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the market-data WebSocket endpoint at {@code /ws/marketdata}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final MarketDataWebSocketHandler handler;

  /**
   * Creates the config.
   *
   * @param handler the market-data handler
   */
  public WebSocketConfig(MarketDataWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/marketdata").setAllowedOriginPatterns("*");
  }
}
