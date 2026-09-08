package dev.kaloyanyordanov.exchange.persistence;

import dev.kaloyanyordanov.exchange.ledger.CashLedgerListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jctools.queues.MpscArrayQueue;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durably audits cash movements (deposits and withdrawals) to the append-only
 * {@code ledger_transactions} table, asynchronously and off the hot path. The cash
 * ledger thread only enqueues a row into a bounded buffer (a non-blocking
 * {@link CashLedgerListener} callback, drop-and-count on full); a dedicated drain
 * thread batches the writes. A slow database backs up only this buffer and never
 * slows the cash ledger.
 */
public final class CashAuditWorker implements CashLedgerListener {

  private final LedgerTransactionRepository repository;
  private final TransactionTemplate transactionTemplate;
  private final MpscArrayQueue<LedgerTransactionEntity> queue;
  private final int maxBatch;
  private final AtomicLong dropped = new AtomicLong();
  private volatile boolean running;
  private Thread worker;

  /**
   * Creates the worker.
   *
   * @param repository         the cash-movement audit repository
   * @param transactionManager the transaction manager
   * @param capacity           the bounded buffer capacity (rounded to a power of two)
   * @param maxBatch           the maximum rows written per transaction
   */
  public CashAuditWorker(
      LedgerTransactionRepository repository,
      PlatformTransactionManager transactionManager,
      int capacity,
      int maxBatch) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive: " + capacity);
    }
    if (maxBatch <= 0) {
      throw new IllegalArgumentException("max batch must be positive: " + maxBatch);
    }
    this.repository = repository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.queue = new MpscArrayQueue<>(capacity);
    this.maxBatch = maxBatch;
  }

  @Override
  public void onDeposit(long account, long amount, long newAvailable, String reference) {
    enqueue(LedgerTransactionType.DEPOSIT, account, amount, newAvailable, reference);
  }

  @Override
  public void onWithdrawal(long account, long amount, long newAvailable, String reference) {
    enqueue(LedgerTransactionType.WITHDRAWAL, account, amount, newAvailable, reference);
  }

  private void enqueue(
      LedgerTransactionType type, long account, long amount, long newAvailable, String reference) {
    LedgerTransactionEntity row =
        new LedgerTransactionEntity(type, account, amount, newAvailable, reference, Instant.now());
    if (!queue.offer(row)) {
      dropped.incrementAndGet();
    }
  }

  /** Starts the drain thread. */
  public void start() {
    running = true;
    worker = new Thread(this::runLoop, "cash-audit");
    worker.start();
  }

  /**
   * Stops the worker, draining outstanding rows first.
   *
   * @throws InterruptedException if interrupted while stopping
   */
  public void stop() throws InterruptedException {
    running = false;
    if (worker != null) {
      worker.join();
      worker = null;
    }
  }

  private void runLoop() {
    while (running || !queue.isEmpty()) {
      List<LedgerTransactionEntity> batch = new ArrayList<>();
      LedgerTransactionEntity row;
      while (batch.size() < maxBatch && (row = queue.poll()) != null) {
        batch.add(row);
      }
      if (batch.isEmpty()) {
        Thread.onSpinWait();
        continue;
      }
      transactionTemplate.executeWithoutResult(status -> repository.saveAll(batch));
    }
  }

  /**
   * The number of movements buffered but not yet persisted.
   *
   * @return the buffered row count
   */
  public int pendingCount() {
    return queue.size();
  }

  /**
   * The number of movements dropped because the buffer was full.
   *
   * @return the dropped count
   */
  public long droppedCount() {
    return dropped.get();
  }
}
