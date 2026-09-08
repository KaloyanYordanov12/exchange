package dev.kaloyanyordanov.exchange.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PairBroadcastersTest {

  private static PairBroadcasters broadcasters() {
    return new PairBroadcasters(
        List.of("BTC-USD", "ETH-USD", "DOGE-USD"),
        new ObjectMapper()::writeValueAsString,
        10,
        4,
        64);
  }

  @Test
  void buildsOneBroadcasterPerPairInOrder() {
    PairBroadcasters broadcasters = broadcasters();
    assertThat(broadcasters.all()).hasSize(3);
    assertThat(broadcasters.all().stream().map(ThrottledBroadcaster::pairId))
        .containsExactly("BTC-USD", "ETH-USD", "DOGE-USD");
  }

  @Test
  void forPairReturnsTheMatchingBroadcasterOrNull() {
    PairBroadcasters broadcasters = broadcasters();
    assertThat(broadcasters.forPair("ETH-USD").pairId()).isEqualTo("ETH-USD");
    assertThat(broadcasters.forPair("NOPE-USD")).isNull();
  }

  @Test
  void startThenStopIsClean() {
    PairBroadcasters broadcasters = broadcasters();
    broadcasters.start();
    broadcasters.stop();
    // No exception; each broadcaster's scheduler was started and shut down.
    assertThat(broadcasters.all()).hasSize(3);
  }
}
