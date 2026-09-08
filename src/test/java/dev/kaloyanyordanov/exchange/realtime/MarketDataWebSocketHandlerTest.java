package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class MarketDataWebSocketHandlerTest {

  private static PairBroadcasters broadcasters() {
    return new PairBroadcasters(
        List.of("BTC-USD", "ETH-USD"), new ObjectMapper()::writeValueAsString, 10, 4, 64);
  }

  private static WebSocketSession session(String id, String query) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.getUri())
        .thenReturn(
            URI.create("ws://localhost/ws/marketdata" + (query == null ? "" : "?" + query)));
    return session;
  }

  @Test
  void connectionRegistersWithTheRequestedPairBroadcaster() throws Exception {
    PairBroadcasters broadcasters = broadcasters();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcasters, 16);

    handler.afterConnectionEstablished(session("s1", "pair=BTC-USD"));

    assertThat(handler.connectionCount()).isEqualTo(1);
    assertThat(broadcasters.forPair("BTC-USD").clientCount()).isEqualTo(1);
    assertThat(broadcasters.forPair("ETH-USD").clientCount()).isZero();
  }

  @Test
  void unknownPairIsClosedAndNotRegistered() throws Exception {
    PairBroadcasters broadcasters = broadcasters();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcasters, 16);
    WebSocketSession session = session("s1", "pair=NOPE-USD");

    handler.afterConnectionEstablished(session);

    assertThat(handler.connectionCount()).isZero();
    verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("unknown or missing pair"));
  }

  @Test
  void missingPairIsClosedAndNotRegistered() throws Exception {
    PairBroadcasters broadcasters = broadcasters();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcasters, 16);
    WebSocketSession session = session("s1", null);

    handler.afterConnectionEstablished(session);

    assertThat(handler.connectionCount()).isZero();
    verify(session).close(CloseStatus.NOT_ACCEPTABLE.withReason("unknown or missing pair"));
  }

  @Test
  void closeUnregistersFromTheRightPairAndStops() throws Exception {
    PairBroadcasters broadcasters = broadcasters();
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcasters, 16);
    WebSocketSession btc = session("s1", "pair=BTC-USD");
    WebSocketSession eth = session("s2", "pair=ETH-USD");

    handler.afterConnectionEstablished(btc);
    handler.afterConnectionEstablished(eth);
    assertThat(broadcasters.forPair("BTC-USD").clientCount()).isEqualTo(1);
    assertThat(broadcasters.forPair("ETH-USD").clientCount()).isEqualTo(1);

    handler.afterConnectionClosed(btc, CloseStatus.NORMAL);

    assertThat(handler.connectionCount()).isEqualTo(1);
    assertThat(broadcasters.forPair("BTC-USD").clientCount()).isZero();
    assertThat(broadcasters.forPair("ETH-USD").clientCount()).isEqualTo(1);
  }

  @Test
  void closingUnknownSessionDoesNothing() {
    MarketDataWebSocketHandler handler = new MarketDataWebSocketHandler(broadcasters(), 16);
    handler.afterConnectionClosed(session("ghost", "pair=BTC-USD"), CloseStatus.NORMAL);
    assertThat(handler.connectionCount()).isZero();
  }
}
