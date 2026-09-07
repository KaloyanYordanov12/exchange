package dev.kaloyanyordanov.exchange.realtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket endpoint for live market data. Each connected session is wrapped in
 * an {@link AsyncClientConnection} (its own buffer + drain thread) and registered
 * with the broadcaster, so the throttling flusher only ever does a non-blocking
 * hand-off and a slow client cannot stall the engine or other clients.
 */
public final class MarketDataWebSocketHandler extends TextWebSocketHandler {

  private final ThrottledBroadcaster broadcaster;
  private final int clientBufferSize;
  private final Map<String, AsyncClientConnection> connections = new ConcurrentHashMap<>();

  /**
   * Creates the handler.
   *
   * @param broadcaster      the broadcaster to register clients with
   * @param clientBufferSize the per-client outbound buffer size
   */
  public MarketDataWebSocketHandler(ThrottledBroadcaster broadcaster, int clientBufferSize) {
    this.broadcaster = broadcaster;
    this.clientBufferSize = clientBufferSize;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    AsyncClientConnection connection =
        new AsyncClientConnection(
            session.getId(),
            clientBufferSize,
            message -> session.sendMessage(new TextMessage(message)));
    connection.start();
    connections.put(session.getId(), connection);
    broadcaster.register(connection);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    AsyncClientConnection connection = connections.remove(session.getId());
    if (connection != null) {
      broadcaster.unregister(connection);
      try {
        connection.stop();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * The number of currently connected sessions.
   *
   * @return the connection count
   */
  public int connectionCount() {
    return connections.size();
  }
}
