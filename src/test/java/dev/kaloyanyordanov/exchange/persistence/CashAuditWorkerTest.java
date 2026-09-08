package dev.kaloyanyordanov.exchange.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class CashAuditWorkerTest {

  private final LedgerTransactionRepository repository =
      mock(LedgerTransactionRepository.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

  @Test
  @SuppressWarnings("unchecked")
  void auditsDepositsAndWithdrawalsToTheLedgerLog() throws InterruptedException {
    when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    CashAuditWorker worker = new CashAuditWorker(repository, txManager, 1024, 100);
    worker.start();
    worker.onDeposit(10L, 500L, 500L, "demo-deposit-1");
    worker.onWithdrawal(10L, 200L, 300L, "demo-withdrawal-1");
    worker.stop(); // drains outstanding rows

    ArgumentCaptor<List<LedgerTransactionEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository, atLeastOnce()).saveAll(captor.capture());
    List<LedgerTransactionEntity> saved =
        captor.getAllValues().stream().flatMap(List::stream).toList();
    assertThat(saved).hasSize(2);

    LedgerTransactionEntity deposit =
        saved.stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.DEPOSIT)
            .findFirst()
            .orElseThrow();
    assertThat(deposit.getAccountId()).isEqualTo(10L);
    assertThat(deposit.getAmount()).isEqualTo(500L);
    assertThat(deposit.getNewCashBalance()).isEqualTo(500L);
    assertThat(deposit.getProviderReference()).isEqualTo("demo-deposit-1");
    assertThat(deposit.getCreatedAt()).isNotNull();

    LedgerTransactionEntity withdrawal =
        saved.stream()
            .filter(row -> row.getTransactionType() == LedgerTransactionType.WITHDRAWAL)
            .findFirst()
            .orElseThrow();
    assertThat(withdrawal.getAmount()).isEqualTo(200L);
    assertThat(withdrawal.getNewCashBalance()).isEqualTo(300L);
    assertThat(withdrawal.getProviderReference()).isEqualTo("demo-withdrawal-1");
  }

  @Test
  void dropsMovementsWhenTheBufferIsFullRatherThanBlocking() {
    CashAuditWorker worker = new CashAuditWorker(repository, txManager, 1, 100); // capacity 1
    // Not started, so nothing drains; the tiny buffer fills and extra rows are dropped.
    worker.onDeposit(1L, 10L, 10L, "a");
    worker.onDeposit(1L, 10L, 20L, "b");
    worker.onDeposit(1L, 10L, 30L, "c");
    assertThat(worker.droppedCount()).isPositive();
  }
}
