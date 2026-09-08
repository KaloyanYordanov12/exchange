package dev.kaloyanyordanov.exchange.candle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandleAggregatorWorkerTest {

  private static final Instant FAR_FUTURE = Instant.ofEpochSecond(4_102_444_800L);

  private static TradeExecuted trade(long price, long quantity, long sequence) {
    return new TradeExecuted(
        new Trade(OrderId.of(1L), OrderId.of(2L), price, quantity, 1L, 2L, sequence));
  }

  @Test
  void aggregatesTradesIntoCandlesOffThePublishThread() throws InterruptedException {
    CandleStore store = new CandleStore();
    CandleAggregatorWorker worker = new CandleAggregatorWorker("BTC-USD", store, 1024, 100);
    worker.start();
    worker.publish(trade(100L, 5L, 0L));
    worker.publish(trade(110L, 3L, 1L));
    worker.publish(trade(90L, 2L, 2L));
    worker.stop(); // drains

    // Trades stamped with a near-identical wall clock fall in the same timeframe
    // buckets; the aggregated volume equals the total traded, and each candle is valid.
    for (Timeframe timeframe : Timeframe.values()) {
      List<Candle> series = store.query("BTC-USD", timeframe, Instant.EPOCH, FAR_FUTURE);
      assertThat(series).isNotEmpty();
      assertThat(series.stream().mapToLong(Candle::volume).sum()).isEqualTo(10L);
      assertThat(series).allSatisfy(candle -> assertThat(CandleInvariants.valid(candle)).isTrue());
    }
  }
}
