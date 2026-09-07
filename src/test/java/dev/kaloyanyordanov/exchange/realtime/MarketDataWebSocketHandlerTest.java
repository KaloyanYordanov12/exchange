package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class MarketDataWebSocketHandlerTest {

  private static ThrottledBroadcaster broadcaster() {
    return new ThrottledBroadcaster(new ObjectMapper()::writeValueAsString, 10, 4, 64);
  }

  private static WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    return session;
  }

  @Test
  void connectionRegistersWithBroadcaster() {
    ThrottledBroadcaster broadcaster = broadcaster();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcaster, 16);

    handler.afterConnectionEstablished(session("s1"));

    assertThat(handler.connectionCount()).isEqualTo(1);
    assertThat(broadcaster.clientCount()).isEqualTo(1);
  }

  @Test
  void closeUnregistersAndStops() {
    ThrottledBroadcaster broadcaster = broadcaster();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcaster, 16);
    WebSocketSession one = session("s1");
    WebSocketSession two = session("s2");

    handler.afterConnectionEstablished(one);
    handler.afterConnectionEstablished(two);
    assertThat(broadcaster.clientCount()).isEqualTo(2);

    handler.afterConnectionClosed(one, CloseStatus.NORMAL);

    assertThat(handler.connectionCount()).isEqualTo(1);
    assertThat(broadcaster.clientCount()).isEqualTo(1);
  }

  @Test
  void closingUnknownSessionDoesNothing() {
    ThrottledBroadcaster broadcaster = broadcaster();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcaster, 16);
    handler.afterConnectionClosed(session("ghost"), CloseStatus.NORMAL);
    assertThat(handler.connectionCount()).isZero();
  }
}
