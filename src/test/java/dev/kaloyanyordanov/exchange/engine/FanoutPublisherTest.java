package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FanoutPublisherTest {

  @Test
  void fansEachEventToEverySink() {
    RecordingEventPublisher a = new RecordingEventPublisher();
    RecordingEventPublisher b = new RecordingEventPublisher();
    FanoutPublisher fanout = new FanoutPublisher(List.of(a, b));

    OrderAccepted event = new OrderAccepted(OrderId.of(1L), Side.BUY, 100L, 5L, 1L, 0L);
    fanout.publish(event);

    assertThat(a.events()).containsExactly(event);
    assertThat(b.events()).containsExactly(event);
    assertThat(fanout.sinkFailures()).isZero();
  }

  @Test
  void isolatesOneFailingSinkFromOthers() {
    List<EngineEvent> delivered = new ArrayList<>();
    EventPublisher throwing =
        event -> {
          throw new IllegalStateException("bad sink");
        };
    EventPublisher healthy = delivered::add;
    FanoutPublisher fanout = new FanoutPublisher(List.of(throwing, healthy));

    OrderAccepted event = new OrderAccepted(OrderId.of(1L), Side.BUY, 100L, 5L, 1L, 0L);
    fanout.publish(event);

    // The healthy sink still received the event; the failure was counted.
    assertThat(delivered).containsExactly(event);
    assertThat(fanout.sinkFailures()).isEqualTo(1L);
  }
}
