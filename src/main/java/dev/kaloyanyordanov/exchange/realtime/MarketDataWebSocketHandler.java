package dev.kaloyanyordanov.exchange.realtime;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket endpoint for live market data. A client subscribes to exactly one pair by
 * connecting to {@code /ws/marketdata?pair=BTC-USD}; the connection is registered with
 * that pair's {@link ThrottledBroadcaster} only, so it receives that pair's book and
 * trades and nothing else. A missing or unknown pair is closed immediately (bad data)
 * rather than silently joined to the wrong stream.
 *
 * <p>Each connected session is wrapped in an {@link AsyncClientConnection} (its own
 * buffer + drain thread), so the throttling flusher only ever does a non-blocking
 * hand-off and a slow client cannot stall the engine or other clients.
 */
public final class MarketDataWebSocketHandler extends TextWebSocketHandler {

  private final PairBroadcasters broadcasters;
  private final int clientBufferSize;
  private final Map<String, Subscription> connections = new ConcurrentHashMap<>();

  /** A live subscription: the client's connection and the broadcaster it joined. */
  private record Subscription(AsyncClientConnection connection, ThrottledBroadcaster broadcaster) {}

  /**
   * Creates the handler.
   *
   * @param broadcasters     the per-pair broadcasters
   * @param clientBufferSize the per-client outbound buffer size
   */
  public MarketDataWebSocketHandler(PairBroadcasters broadcasters, int clientBufferSize) {
    this.broadcasters = broadcasters;
    this.clientBufferSize = clientBufferSize;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String pair = pairOf(session.getUri());
    ThrottledBroadcaster broadcaster = pair == null ? null : broadcasters.forPair(pair);
    if (broadcaster == null) {
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unknown or missing pair"));
      return;
    }
    AsyncClientConnection connection =
        new AsyncClientConnection(
            session.getId(),
            clientBufferSize,
            message -> session.sendMessage(new TextMessage(message)));
    connection.start();
    connections.put(session.getId(), new Subscription(connection, broadcaster));
    broadcaster.register(connection);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Subscription subscription = connections.remove(session.getId());
    if (subscription != null) {
      subscription.broadcaster().unregister(subscription.connection());
      try {
        subscription.connection().stop();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * The number of currently connected (subscribed) sessions.
   *
   * @return the connection count
   */
  public int connectionCount() {
    return connections.size();
  }

  /** Extracts the {@code pair} query parameter from the handshake URI, or null. */
  private static String pairOf(URI uri) {
    if (uri == null || uri.getQuery() == null) {
      return null;
    }
    for (String part : uri.getQuery().split("&")) {
      int eq = part.indexOf('=');
      if (eq > 0 && "pair".equals(part.substring(0, eq))) {
        String value = part.substring(eq + 1);
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }
}
