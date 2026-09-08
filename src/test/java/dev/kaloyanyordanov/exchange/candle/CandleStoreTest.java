package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CandleStoreTest {

  private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

  private static Candle candle(Instant openTime, long close, CandleSource source) {
    return new Candle("BTC-USD", Timeframe.M1, openTime, 100L, 110L, 90L, close, 5L, source);
  }

  @Test
  void upsertsAndQueriesByRange() {
    CandleStore store = new CandleStore();
    store.upsert(candle(T0, 100L, CandleSource.REAL));
    store.upsert(candle(T0.plusSeconds(60), 101L, CandleSource.REAL));
    store.upsert(candle(T0.plusSeconds(120), 102L, CandleSource.REAL));

    assertThat(store.query("BTC-USD", Timeframe.M1, T0, T0.plusSeconds(60)))
        .extracting(Candle::close)
        .containsExactly(100L, 101L);
    assertThat(store.query("BTC-USD", Timeframe.M5, T0, T0.plusSeconds(600))).isEmpty();
    assertThat(store.query("ETH-USD", Timeframe.M1, T0, T0.plusSeconds(600))).isEmpty();
  }

  @Test
  void latestUpsertForBucketWins() {
    CandleStore store = new CandleStore();
    store.upsert(candle(T0, 100L, CandleSource.REAL));
    store.upsert(candle(T0, 105L, CandleSource.REAL)); // same bucket, updated close
    assertThat(store.query("BTC-USD", Timeframe.M1, T0, T0)).extracting(Candle::close)
        .containsExactly(105L);
  }

  @Test
  void realBoundaryIsTheFirstRealCandleAfterSeededHistory() {
    CandleStore store = new CandleStore();
    store.upsert(candle(T0, 100L, CandleSource.SEEDED));
    store.upsert(candle(T0.plusSeconds(60), 101L, CandleSource.SEEDED));
    store.upsert(candle(T0.plusSeconds(120), 102L, CandleSource.REAL));
    assertThat(store.realBoundary("BTC-USD", Timeframe.M1)).contains(T0.plusSeconds(120));
  }

  @Test
  void realBoundaryEmptyWhenOnlySeeded() {
    CandleStore store = new CandleStore();
    store.upsert(candle(T0, 100L, CandleSource.SEEDED));
    assertThat(store.realBoundary("BTC-USD", Timeframe.M1)).isEmpty();
  }
}
