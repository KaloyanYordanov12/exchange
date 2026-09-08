package dev.kaloyanyordanov.exchange.realtime;

import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * End-to-end WebSocket delivery: a real client connects, orders are matched, and
 * the client receives throttled market-data frames. Excluded from PIT (slow full
 * context); the fast unit tests carry throttle/isolation mutation coverage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketDataWebSocketIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private ExchangeRegistry registry;

  @Test
  void connectedClientReceivesThrottledMarketData() throws Exception {
    Queue<String> received = new ConcurrentLinkedQueue<>();
    WebSocketSession session = connectWithRetry(received);

    try {
      // A crossing pair on DOGE-USD produces a trade and book changes, which the
      // broadcaster flushes to the connected client at the throttled rate.
      registry.place("DOGE-USD", 2L, Side.SELL, 100L, 5L);
      registry.place("DOGE-USD", 1L, Side.BUY, 100L, 5L);

      // Generous window: under heavy CI load the async broadcast can lag well past a
      // few seconds. Both a book snapshot and the trade tape must arrive.
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () ->
                  received.stream().anyMatch(m -> m.contains("\"type\":\"book\""))
                      && received.stream().anyMatch(m -> m.contains("\"type\":\"trades\"")));
    } finally {
      session.close(CloseStatus.NORMAL);
    }
  }

  /** The handshake can transiently fail under heavy CI load; retry a few times. */
  private WebSocketSession connectWithRetry(Queue<String> received) throws Exception {
    TextWebSocketHandler handler =
        new TextWebSocketHandler() {
          @Override
          protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
          }
        };
    Exception last = null;
    for (int attempt = 0; attempt < 4; attempt++) {
      try {
        return new StandardWebSocketClient()
            .execute(handler, "ws://localhost:" + port + "/ws/marketdata?pair=DOGE-USD")
            .get(20, TimeUnit.SECONDS);
      } catch (Exception failure) {
        last = failure;
        Thread.sleep(500L);
      }
    }
    throw last;
  }
}
