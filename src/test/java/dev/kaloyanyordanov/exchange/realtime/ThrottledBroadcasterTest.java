package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.BookChanged;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ThrottledBroadcasterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final class RecordingSink implements ClientSink {
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void deliver(String message) {
      messages.add(message);
    }
  }

  private static ThrottledBroadcaster broadcaster() {
    return new ThrottledBroadcaster(MAPPER::writeValueAsString, 10, 4, 1024);
  }

  private static BookChanged bookWithBid(long price, long quantity) {
    return new BookChanged(
        new BookSnapshot(List.of(new PriceLevel(price, quantity)), List.of()));
  }

  private static TradeExecuted trade(long price, long quantity, long sequence) {
    return new TradeExecuted(
        new Trade(OrderId.of(1L), OrderId.of(2L), price, quantity, 1L, 2L, sequence));
  }

  @Test
  void rejectsInvalidConfiguration() {
    JsonSerializer serializer = MAPPER::writeValueAsString;
    assertThatThrownBy(() -> new ThrottledBroadcaster(serializer, 0, 4, 16))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ThrottledBroadcaster(serializer, 10, 0, 16))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void coalescesManyBookChangesIntoOneLatestSnapshot() throws Exception {
    ThrottledBroadcaster broadcaster = broadcaster();
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    for (int i = 1; i <= 100; i++) {
      broadcaster.publish(bookWithBid(50L, i));
    }
    broadcaster.flush();

    assertThat(sink.messages).hasSize(1);
    JsonNode message = MAPPER.readTree(sink.messages.get(0));
    assertThat(message.get("type").asText()).isEqualTo("book");
    assertThat(message.get("bids").get(0).get("quantity").asLong()).isEqualTo(100L);
  }

  @Test
  void quietFlushSendsNothing() {
    ThrottledBroadcaster broadcaster = broadcaster();
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    broadcaster.publish(bookWithBid(50L, 1L));
    broadcaster.flush();
    broadcaster.flush(); // nothing new

    assertThat(sink.messages).hasSize(1);
  }

  @Test
  void batchesTradesUpToTheLimitPerFlush() throws Exception {
    ThrottledBroadcaster broadcaster = broadcaster(); // maxTradesPerFlush = 4
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    for (int i = 0; i < 10; i++) {
      broadcaster.publish(trade(100L, 1L, i));
    }
    broadcaster.flush();
    broadcaster.flush();
    broadcaster.flush();

    // 10 trades over flushes of at most 4 -> 4, 4, 2 across three messages.
    List<Integer> batchSizes = new ArrayList<>();
    for (String message : sink.messages) {
      batchSizes.add(MAPPER.readTree(message).get("trades").size());
    }
    assertThat(batchSizes).containsExactly(4, 4, 2);
  }

  @Test
  void emitsBookAndTradesInOneFlush() throws Exception {
    ThrottledBroadcaster broadcaster = broadcaster();
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    broadcaster.publish(bookWithBid(50L, 5L));
    broadcaster.publish(trade(100L, 2L, 0L));
    broadcaster.flush();

    assertThat(sink.messages).hasSize(2);
    List<String> types = new ArrayList<>();
    for (String message : sink.messages) {
      types.add(MAPPER.readTree(message).get("type").asText());
    }
    assertThat(types).containsExactlyInAnyOrder("book", "trades");
  }

  @Test
  void ignoresNonMarketEvents() {
    ThrottledBroadcaster broadcaster = broadcaster();
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    broadcaster.publish(new OrderAccepted(OrderId.of(1L), Side.BUY, 100L, 5L, 1L, 0L));
    broadcaster.flush();

    assertThat(sink.messages).isEmpty();
  }

  @Test
  void registerAndUnregisterTrackClients() {
    ThrottledBroadcaster broadcaster = broadcaster();
    RecordingSink sink = new RecordingSink();
    assertThat(broadcaster.clientCount()).isZero();
    broadcaster.register(sink);
    assertThat(broadcaster.clientCount()).isEqualTo(1);
    broadcaster.unregister(sink);
    assertThat(broadcaster.clientCount()).isZero();
  }

  @Test
  void oneFailingClientDoesNotStopOthers() {
    ThrottledBroadcaster broadcaster = broadcaster();
    ClientSink failing =
        message -> {
          throw new IllegalStateException("boom");
        };
    RecordingSink healthy = new RecordingSink();
    broadcaster.register(failing);
    broadcaster.register(healthy);

    broadcaster.publish(bookWithBid(50L, 1L));
    broadcaster.flush();

    assertThat(healthy.messages).hasSize(1);
  }

  @Test
  void serializationFailureIsCountedNotThrown() {
    JsonSerializer failing =
        value -> {
          throw new JacksonException("boom") {};
        };
    ThrottledBroadcaster broadcaster = new ThrottledBroadcaster(failing, 10, 4, 16);
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    broadcaster.publish(bookWithBid(50L, 1L));
    broadcaster.flush();

    assertThat(sink.messages).isEmpty();
    assertThat(broadcaster.serializationFailures()).isEqualTo(1L);
  }
}
