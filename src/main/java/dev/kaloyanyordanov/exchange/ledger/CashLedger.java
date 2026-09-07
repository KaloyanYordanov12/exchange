package dev.kaloyanyordanov.exchange.ledger;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MpscArrayQueue;

/**
 * The single shared owner of every account's <b>cash</b> (the quote currency),
 * across all five pair engines. Cash is the one balance shared between pairs — an
 * account's asset holdings are per-pair and owned by each engine's own matching
 * thread, but its cash is spendable on any pair, so it cannot live inside any one
 * engine.
 *
 * <p><b>How five matching threads safely share one cash balance.</b> They never
 * touch it by shared memory. This ledger is an actor: one dedicated thread drains a
 * bounded lock-free MPSC ingress and applies every cash operation — deposit,
 * withdraw, reserve, release, settle — <em>serially</em>. The engines are producers
 * that offer immutable command messages; only this thread mutates cash. So
 * check-then-commit (an affordability check and its debit) is atomic without a
 * lock, exactly as each engine's single-threaded core is, and a concurrent cross-
 * pair order flow can never race, double-spend, or drive cash negative.
 *
 * <p><b>Buying-power reservations.</b> Because affordability can no longer be judged
 * synchronously inside a matching thread (cash is off-thread now), a buy reserves
 * its maximum cost ({@code limitPrice × quantity}) here <em>before</em> it enters a
 * book. The matcher then fills it freely; each fill {@link #settle} moves the exact
 * cost from the buyer's reserved cash to the seller's available cash, and any price
 * improvement is {@link #release}d back. Reserved cash is never withdrawable, so a
 * withdrawal can never free funds committed to an open order.
 *
 * <p><b>Determinism.</b> As with the engines, the realized order in which concurrent
 * producers land in the ingress is timing-dependent, but this thread processes that
 * realized order deterministically and the conservation invariant
 * ({@code Σ(available+reserved) == Σdeposited − Σwithdrawn}) holds under every
 * interleaving.
 */
public final class CashLedger {

  // Internal commands. Private because the public API is the methods below; the
  // engines and services never construct these directly.
  private sealed interface CashCommand
      permits Deposit, Withdrawal, Reservation, Release, Settlement, SnapshotRequest {}

  private record Deposit(long account, long amount, String reference) implements CashCommand {}

  private record Withdrawal(long requestId, long account, long amount, String reference)
      implements CashCommand {}

  private record Reservation(long requestId, long account, long amount) implements CashCommand {}

  private record Release(long account, long amount) implements CashCommand {}

  private record Settlement(long buyerAccount, long sellerAccount, long amount)
      implements CashCommand {}

  private record SnapshotRequest(long requestId) implements CashCommand {}

  private record StampedSnapshot(long requestId, CashSnapshot snapshot) {}

  // The cash state, mutated only on this ledger's single thread (below).
  private final CashBook book = new CashBook();

  private final MpscArrayQueue<CashCommand> ingress;
  private final int capacity;
  private volatile boolean accepting;
  private Thread worker;

  private final ConcurrentHashMap<Long, ReservationOutcome> reserveOutcomes =
      new ConcurrentHashMap<>();
  private final AtomicLong reserveRequestIds = new AtomicLong();
  private final ConcurrentHashMap<Long, WithdrawalResult> withdrawOutcomes =
      new ConcurrentHashMap<>();
  private final AtomicLong withdrawRequestIds = new AtomicLong();
  private final AtomicReference<StampedSnapshot> latestSnapshot = new AtomicReference<>();
  private final AtomicLong snapshotRequestIds = new AtomicLong();

  /**
   * Creates a cash ledger.
   *
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   */
  public CashLedger(int requestedCapacity) {
    if (requestedCapacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive: " + requestedCapacity);
    }
    this.ingress = new MpscArrayQueue<>(requestedCapacity);
    this.capacity = ingress.capacity();
  }

  /**
   * The actual ingress capacity (the requested value rounded up to a power of two).
   *
   * @return the ingress capacity
   */
  public int ingressCapacity() {
    return capacity;
  }

  /** Starts the cash-ledger thread. */
  public synchronized void start() {
    if (worker != null) {
      throw new IllegalStateException("cash ledger already started");
    }
    accepting = true;
    worker = new Thread(this::runLoop, "cash-ledger-thread");
    worker.start();
  }

  /**
   * Stops accepting new requests, drains everything already enqueued, and joins the
   * thread. Callers must quiesce the engines first so no settlement is lost.
   *
   * @throws InterruptedException if interrupted while awaiting the thread
   */
  public synchronized void stop() throws InterruptedException {
    accepting = false;
    if (worker != null) {
      worker.join();
      worker = null;
    }
  }

  private void runLoop() {
    while (accepting || !ingress.isEmpty()) {
      CashCommand command = ingress.poll();
      if (command == null) {
        Thread.onSpinWait();
        continue;
      }
      process(command);
    }
  }

  /**
   * Applies one command on the cash-ledger thread. Package-private so the serial
   * logic can be exercised deterministically in tests without a thread.
   *
   * @param command the command to apply
   */
  void process(CashCommand command) {
    switch (command) {
      case Deposit deposit -> book.deposit(deposit.account(), deposit.amount());
      case Withdrawal withdrawal -> {
        boolean applied = book.withdraw(withdrawal.account(), withdrawal.amount());
        withdrawOutcomes.put(
            withdrawal.requestId(),
            applied ? WithdrawalResult.APPLIED : WithdrawalResult.INSUFFICIENT_FUNDS);
      }
      case Reservation reservation -> {
        boolean reserved = book.reserve(reservation.account(), reservation.amount());
        reserveOutcomes.put(
            reservation.requestId(),
            reserved ? ReservationOutcome.RESERVED : ReservationOutcome.INSUFFICIENT_FUNDS);
      }
      case Release release -> book.release(release.account(), release.amount());
      case Settlement settlement ->
          book.settle(
              settlement.buyerAccount(), settlement.sellerAccount(), settlement.amount());
      case SnapshotRequest request ->
          latestSnapshot.set(new StampedSnapshot(request.requestId(), book.snapshot()));
    }
  }

  /**
   * Deposits cash into an account (credited to available). Non-blocking.
   *
   * @param account   the account to credit
   * @param amount    the amount in scaled integer quote units; must be positive
   * @param reference the payment provider's reference
   * @return {@code true} if the command was enqueued, {@code false} if the ledger is
   *     busy or not running
   */
  public boolean deposit(long account, long amount, String reference) {
    if (amount <= 0) {
      throw new IllegalArgumentException("deposit amount must be positive: " + amount);
    }
    Objects.requireNonNull(reference, "reference");
    return accepting && ingress.offer(new Deposit(account, amount, reference));
  }

  /**
   * Withdraws cash from an account's <em>available</em> balance and waits for the
   * outcome. Reserved cash (committed to open orders) is never touched, so a
   * withdrawal can never free committed funds or drive a balance negative.
   *
   * @param account   the account to debit
   * @param amount    the amount in scaled integer quote units; must be positive
   * @param reference the reference recorded for the withdrawal
   * @param timeout   how long to wait for the outcome
   * @return the withdrawal result
   */
  public WithdrawalResult withdraw(long account, long amount, String reference, Duration timeout) {
    if (amount <= 0) {
      throw new IllegalArgumentException("withdraw amount must be positive: " + amount);
    }
    Objects.requireNonNull(reference, "reference");
    long requestId = withdrawRequestIds.incrementAndGet();
    if (!accepting || !ingress.offer(new Withdrawal(requestId, account, amount, reference))) {
      return WithdrawalResult.UNAVAILABLE;
    }
    return awaitOutcome(withdrawOutcomes, requestId, timeout, WithdrawalResult.UNAVAILABLE);
  }

  /**
   * Reserves buying power for a buy order and waits for the outcome: an atomic
   * check-and-hold on the cash thread that moves {@code amount} from available to
   * reserved, or fails if the account cannot afford it.
   *
   * @param account the account
   * @param amount  the cost to reserve in scaled integer quote units; must be positive
   * @param timeout how long to wait for the outcome
   * @return the reservation outcome
   */
  public ReservationOutcome reserve(long account, long amount, Duration timeout) {
    if (amount <= 0) {
      throw new IllegalArgumentException("reserve amount must be positive: " + amount);
    }
    long requestId = reserveRequestIds.incrementAndGet();
    if (!accepting || !ingress.offer(new Reservation(requestId, account, amount))) {
      return ReservationOutcome.UNAVAILABLE;
    }
    return awaitOutcome(reserveOutcomes, requestId, timeout, ReservationOutcome.UNAVAILABLE);
  }

  /**
   * Releases reserved cash back to available (unspent buying power from a filled or
   * cancelled order). Applied on the cash thread; spins until enqueued because a
   * release must never be dropped (it is guaranteed to be drained quickly).
   *
   * @param account the account
   * @param amount  the amount to release; a zero amount is a no-op
   */
  public void release(long account, long amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("release amount must be non-negative: " + amount);
    }
    if (amount == 0L) {
      return;
    }
    offerUntilEnqueued(new Release(account, amount));
  }

  /**
   * Settles a fill's cash: moves {@code amount} from the buyer's reserved cash to
   * the seller's available cash. Applied on the cash thread; spins until enqueued
   * because a settlement must never be dropped.
   *
   * @param buyerAccount  the buying account (reserved is debited)
   * @param sellerAccount the selling account (available is credited)
   * @param amount        the cash to move; must be positive
   */
  public void settle(long buyerAccount, long sellerAccount, long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("settle amount must be positive: " + amount);
    }
    offerUntilEnqueued(new Settlement(buyerAccount, sellerAccount, amount));
  }

  private void offerUntilEnqueued(CashCommand command) {
    while (!ingress.offer(command)) {
      Thread.onSpinWait();
    }
  }

  /**
   * Requests a consistent snapshot of the whole cash ledger, produced on the cash
   * thread. Never touches the live ledger from the caller.
   *
   * @param timeout how long to wait for the snapshot
   * @return the snapshot, or empty if the request was rejected or timed out
   */
  public Optional<CashSnapshot> snapshot(Duration timeout) {
    long requestId = snapshotRequestIds.incrementAndGet();
    if (!accepting || !ingress.offer(new SnapshotRequest(requestId))) {
      return Optional.empty();
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      StampedSnapshot stamped = latestSnapshot.get();
      if (stamped != null && stamped.requestId() >= requestId) {
        return Optional.of(stamped.snapshot());
      }
      LockSupport.parkNanos(100_000L);
    }
    return Optional.empty();
  }

  private static <T> T awaitOutcome(
      ConcurrentHashMap<Long, T> outcomes, long requestId, Duration timeout, T unavailable) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      T outcome = outcomes.remove(requestId);
      if (outcome != null) {
        return outcome;
      }
      LockSupport.parkNanos(100_000L);
    }
    outcomes.remove(requestId);
    return unavailable;
  }
}
