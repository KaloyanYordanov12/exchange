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
import dev.kaloyanyordanov.exchange.engine.CashDeposited;
import dev.kaloyanyordanov.exchange.engine.CashWithdrawn;
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

  private final AccountRepository accounts = mock(AccountRepository.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final TradeRepository trades = mock(TradeRepository.class);
  private final LedgerTransactionRepository ledgerTransactions =
      mock(LedgerTransactionRepository.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

  private PersistenceWorker worker() {
    when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return new PersistenceWorker(
        accounts, orders, trades, ledgerTransactions, txManager, 1024, 100);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsAndSavesOrdersTradesAndAccounts() {
    PersistenceWorker worker = worker();
    Trade trade = new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 10L, 20L, 7L);

    worker.persistBatch(
        List.of(
            new OrderAccepted(OrderId.of(3L), Side.BUY, 100L, 5L, 10L, 4L),
            new TradeExecuted(trade),
            new AccountUpdated(10L, 900L, 12L)));

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

    ArgumentCaptor<List<AccountEntity>> accountCaptor = ArgumentCaptor.forClass(List.class);
    verify(accounts).saveAll(accountCaptor.capture());
    AccountEntity account = accountCaptor.getValue().get(0);
    assertThat(account.getAccountId()).isEqualTo(10L);
    assertThat(account.getCash()).isEqualTo(900L);
    assertThat(account.getAsset()).isEqualTo(12L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsAndSavesCashMovements() {
    PersistenceWorker worker = worker();

    worker.persistBatch(
        List.<EngineEvent>of(
            new CashDeposited(10L, 500L, 500L, "demo-deposit-1"),
            new CashWithdrawn(10L, 200L, 300L, "demo-withdrawal-1")));

    ArgumentCaptor<List<LedgerTransactionEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(ledgerTransactions).saveAll(captor.capture());
    List<LedgerTransactionEntity> saved = captor.getValue();
    assertThat(saved).hasSize(2);

    LedgerTransactionEntity deposit = saved.get(0);
    assertThat(deposit.getTransactionType()).isEqualTo(LedgerTransactionType.DEPOSIT);
    assertThat(deposit.getAccountId()).isEqualTo(10L);
    assertThat(deposit.getAmount()).isEqualTo(500L);
    assertThat(deposit.getNewCashBalance()).isEqualTo(500L);
    assertThat(deposit.getProviderReference()).isEqualTo("demo-deposit-1");
    assertThat(deposit.getCreatedAt()).isNotNull();

    LedgerTransactionEntity withdrawal = saved.get(1);
    assertThat(withdrawal.getTransactionType()).isEqualTo(LedgerTransactionType.WITHDRAWAL);
    assertThat(withdrawal.getAmount()).isEqualTo(200L);
    assertThat(withdrawal.getNewCashBalance()).isEqualTo(300L);
    assertThat(withdrawal.getProviderReference()).isEqualTo("demo-withdrawal-1");
  }

  @Test
  void ignoresRejectionsAndBookSnapshots() {
    PersistenceWorker worker = worker();

    worker.persistBatch(
        List.<EngineEvent>of(
            new OrderRejected(OrderId.of(1L), OrderRejected.RejectReason.INSUFFICIENT_CASH, 1L),
            new BookChanged(new BookSnapshot(List.of(), List.of()))));

    verify(orders, never()).saveAll(any());
    verify(trades, never()).saveAll(any());
    verify(accounts, never()).saveAll(any());
    verify(ledgerTransactions, never()).saveAll(any());
  }

  @Test
  void publishDelegatesToTheBufferWithoutBlocking() {
    PersistenceWorker worker = worker();
    worker.publish(new AccountUpdated(1L, 100L, 5L));
    assertThat(worker.pendingCount()).isEqualTo(1);
    assertThat(worker.failureCount()).isZero();
  }
}
