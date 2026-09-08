package dev.kaloyanyordanov.exchange.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.AccountUpdated;
import dev.kaloyanyordanov.exchange.engine.BookChanged;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.OrderAccepted;
import dev.kaloyanyordanov.exchange.engine.OrderRejected;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PersistenceWorkerTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final TradeRepository trades = mock(TradeRepository.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

  private PersistenceWorker worker() {
    when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return new PersistenceWorker(orders, trades, txManager, 1024, 100);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsAndSavesOrdersAndTrades() {
    PersistenceWorker worker = worker();
    Trade trade = new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 10L, 20L, 7L);

    worker.persistBatch(
        List.of(
            new OrderAccepted(OrderId.of(3L), Side.BUY, 100L, 5L, 10L, 4L),
            new TradeExecuted(trade),
            new AccountUpdated(10L, 12L))); // asset update is not part of this audit log

    ArgumentCaptor<List<OrderEntity>> orderCaptor = ArgumentCaptor.forClass(List.class);
    verify(orders).saveAll(orderCaptor.capture());
    OrderEntity order = orderCaptor.getValue().get(0);
    assertThat(order.getOrderId()).isEqualTo(3L);
    assertThat(order.getSide()).isEqualTo(Side.BUY);
    assertThat(order.getPrice()).isEqualTo(100L);
    assertThat(order.getQuantity()).isEqualTo(5L);
    assertThat(order.getAccountId()).isEqualTo(10L);
    assertThat(order.getArrivalSequence()).isEqualTo(4L);

    ArgumentCaptor<List<TradeEntity>> tradeCaptor = ArgumentCaptor.forClass(List.class);
    verify(trades).saveAll(tradeCaptor.capture());
    TradeEntity tradeEntity = tradeCaptor.getValue().get(0);
    assertThat(tradeEntity.getTradeSequence()).isEqualTo(7L);
    assertThat(tradeEntity.getBuyOrderId()).isEqualTo(1L);
    assertThat(tradeEntity.getSellOrderId()).isEqualTo(2L);
    assertThat(tradeEntity.getPrice()).isEqualTo(100L);
    assertThat(tradeEntity.getQuantity()).isEqualTo(5L);
    assertThat(tradeEntity.getBuyerAccountId()).isEqualTo(10L);
    assertThat(tradeEntity.getSellerAccountId()).isEqualTo(20L);
  }

  @Test
  void ignoresRejectionsBookSnapshotsAndAssetUpdates() {
    PersistenceWorker worker = worker();

    worker.persistBatch(
        List.<EngineEvent>of(
            new OrderRejected(OrderId.of(1L), OrderRejected.RejectReason.INSUFFICIENT_CASH, 1L),
            new BookChanged(new BookSnapshot(List.of(), List.of())),
            new AccountUpdated(1L, 5L)));

    verify(orders, never()).saveAll(any());
    verify(trades, never()).saveAll(any());
  }

  @Test
  void publishDelegatesToTheBufferWithoutBlocking() {
    PersistenceWorker worker = worker();
    worker.publish(new AccountUpdated(1L, 5L));
    assertThat(worker.pendingCount()).isEqualTo(1);
    assertThat(worker.failureCount()).isZero();
  }
}
