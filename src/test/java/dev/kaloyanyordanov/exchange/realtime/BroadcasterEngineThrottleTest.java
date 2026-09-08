package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the broadcaster throttles a flood of engine events: many book changes
 * driven straight through the engine collapse into a single coalesced snapshot on
 * flush — not one message per change — while the engine still processes them all.
 */
class BroadcasterEngineThrottleTest {

  private static final Symbol SYMBOL = new Symbol("BTC", "USD", 1L, 1L);

  private static final class RecordingSink implements ClientSink {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void deliver(String message) {
      messages.add(message);
    }
  }

  @Test
  void floodOfBookChangesCollapsesToOneSnapshotPerFlush() throws InterruptedException {
    ThrottledBroadcaster broadcaster =
        new ThrottledBroadcaster("BTC-USD", new ObjectMapper()::writeValueAsString, 10, 256, 4096);
    RecordingSink sink = new RecordingSink();
    broadcaster.register(sink);

    // Drive 500 resting orders through the engine -> 500 BookChanged events.
    MatchingEngine engine = new MatchingEngine(SYMBOL, 1024, broadcaster);
    engine.start();
    int orders = 500;
    for (int i = 0; i < orders; i++) {
      SubmitOrder order = new SubmitOrder(OrderId.of(i), Side.BUY, 100L - (i % 50), 1L, 1L);
      while (engine.submit(order) != SubmitResult.ENQUEUED) {
        Thread.onSpinWait();
      }
    }
    engine.stop(); // drains all before returning

    broadcaster.flush();

    // The engine processed every order, but the flood coalesced to one snapshot.
    assertThat(engine.restingOrders()).hasSize(orders);
    assertThat(sink.messages).hasSize(1);
  }
}
