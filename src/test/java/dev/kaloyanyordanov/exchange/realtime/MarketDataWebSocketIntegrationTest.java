package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
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

  @Autowired private MatchingEngine engine;

  @Test
  void connectedClientReceivesThrottledMarketData() throws Exception {
    Queue<String> received = new ConcurrentLinkedQueue<>();
    StandardWebSocketClient client = new StandardWebSocketClient();
    WebSocketSession session =
        client
            .execute(
                new TextWebSocketHandler() {
                  @Override
                  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    received.add(message.getPayload());
                  }
                },
                "ws://localhost:" + port + "/ws/marketdata")
            .get(5, TimeUnit.SECONDS);

    try {
      // A crossing pair produces a trade and book changes, which the broadcaster
      // flushes to the connected client at the throttled rate.
      engine.submit(new SubmitOrder(OrderId.of(9_000_001L), Side.SELL, 100L, 5L, 2L));
      engine.submit(new SubmitOrder(OrderId.of(9_000_002L), Side.BUY, 100L, 5L, 1L));

      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  received.stream()
                      .anyMatch(message -> message.contains("\"type\":\"book\"")));
      assertThat(received).anyMatch(message -> message.contains("\"type\":\"trades\""));
    } finally {
      session.close(CloseStatus.NORMAL);
    }
  }
}
