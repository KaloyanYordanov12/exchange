package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.PairProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleSeedRunnerTest {

  private static final Instant FAR_FUTURE = Instant.ofEpochSecond(4_102_444_800L);
  private static final List<PairProperties> PAIRS =
      List.of(new PairProperties("DOGE", "USD", 1L, 1L, 80_000L));

  @Test
  void disabledRunnerSeedsNothing() {
    CandleStore store = new CandleStore();
    CandleSeedRunner runner = new CandleSeedRunner(PAIRS, store, false, 42L);
    assertThat(runner.enabled()).isFalse();
    runner.run();
    assertThat(store.query("DOGE-USD", Timeframe.D1, Instant.EPOCH, FAR_FUTURE)).isEmpty();
  }

  @Test
  void enabledRunnerSeedsLabeledHistory() {
    CandleStore store = new CandleStore();
    CandleSeedRunner runner = new CandleSeedRunner(PAIRS, store, true, 42L);
    runner.run();
    List<Candle> series = store.query("DOGE-USD", Timeframe.D1, Instant.EPOCH, FAR_FUTURE);
    assertThat(series).isNotEmpty();
    assertThat(series).allSatisfy(c -> assertThat(c.source()).isEqualTo(CandleSource.SEEDED));
  }
}
