package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.FillPolicy;
import dev.kaloyanyordanov.exchange.book.Matcher;
import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderBook;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.ledger.Account;
import dev.kaloyanyordanov.exchange.ledger.LedgerView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MpscArrayQueue;

/**
 * The single-threaded matching core. Many producers offer {@link Command}s to a
 * bounded lock-free MPSC ring buffer; one dedicated matching thread drains it and
 * applies each command to the order book, one at a time, deterministically.
 *
 * <p><b>The matching thread is the sole owner of the book, the ledger policy, and
 * the sequence counters.</b> No other thread touches them and there is no lock on
 * the book — the single-threaded ownership is the correctness guarantee. The only
 * cross-thread channels are the lock-free ingress queue (in) and the
 * {@link EventPublisher} (out). Because processing is serial, the same command
 * sequence always yields the same book and events.
 *
 * <p>The book-inspection methods ({@link #snapshot()}, {@link #restingOrders()},
 * {@link #isCrossed()}) touch the book directly and are intended for use only
 * after {@link #stop()} has joined the matching thread.
 */
public final class MatchingEngine {

  private final MpscArrayQueue<Command> ingress;
  private final int capacity;
  private final OrderBook book;
  private final FillPolicy policy;
  private final EventPublisher publisher;
  // Optional: when present, balance snapshots are published after settlement and
  // engine snapshots include balances. Read only on the matching thread.
  private final LedgerView ledgerView;

  // Touched only by the matching thread (or by a caller in a single-threaded
  // test via processCommand); never shared concurrently.
  private long arrivalSequence;
  private long tradeSequence;
  private long cumulativeCashFromBuyers;
  private long cumulativeCashToSellers;
  private long cumulativeAssetFromSellers;
  private long cumulativeAssetToBuyers;
  // Captured at construction (the ledger is funded before the engine is built).
  private final long initialTotalCash;
  private final long initialTotalAsset;

  // On-demand snapshot channel: the matching thread publishes here; requesters
  // poll for a stamp at or beyond their request id.
  private final AtomicReference<StampedSnapshot> latestSnapshot = new AtomicReference<>();
  private final AtomicLong snapshotRequestIds = new AtomicLong();

  private volatile boolean accepting;
  private Thread worker;

  private record StampedSnapshot(long requestId, EngineSnapshot snapshot) {}

  /**
   * Creates an engine with unconstrained matching (no ledger).
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   */
  public MatchingEngine(Symbol symbol, int requestedCapacity, EventPublisher publisher) {
    this(symbol, requestedCapacity, publisher, FillPolicy.UNCONSTRAINED, null);
  }

  /**
   * Creates an engine with an explicit fill policy (affordability + settlement)
   * and no balance publishing.
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   * @param policy            the fill policy applied by the matcher
   */
  public MatchingEngine(
      Symbol symbol, int requestedCapacity, EventPublisher publisher, FillPolicy policy) {
    this(symbol, requestedCapacity, publisher, policy, null);
  }

  /**
   * Creates an engine that also publishes {@link AccountUpdated} balance
   * snapshots after settlement, read from {@code accountView} on the matching
   * thread.
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   * @param policy            the fill policy applied by the matcher
   * @param ledgerView        ledger view read on the matching thread, or {@code null}
   */
  public MatchingEngine(
      Symbol symbol,
      int requestedCapacity,
      EventPublisher publisher,
      FillPolicy policy,
      LedgerView ledgerView) {
    Objects.requireNonNull(symbol, "symbol");
    if (requestedCapacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive: " + requestedCapacity);
    }
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.ledgerView = ledgerView;
    this.ingress = new MpscArrayQueue<>(requestedCapacity);
    this.capacity = ingress.capacity();
    this.book = new OrderBook(symbol);
    this.initialTotalCash = ledgerView != null ? ledgerView.totalCash() : 0L;
    this.initialTotalAsset = ledgerView != null ? ledgerView.totalAsset() : 0L;
  }

  /**
   * The actual ingress capacity (the requested value rounded up to a power of
   * two). At most this many commands can be queued before back-pressure.
   *
   * @return the ingress capacity
   */
  public int ingressCapacity() {
    return capacity;
  }

  /**
   * Offers a command to the ingress queue from any producer thread. Never blocks.
   *
   * @param command the command to submit
   * @return {@link SubmitResult#ENQUEUED} on success, {@link SubmitResult#REJECTED_BUSY}
   *     if the queue is full, or {@link SubmitResult#REJECTED_NOT_RUNNING} if the
   *     engine is not accepting commands
   */
  public SubmitResult submit(Command command) {
    Objects.requireNonNull(command, "command");
    if (!accepting) {
      return SubmitResult.REJECTED_NOT_RUNNING;
    }
    return ingress.offer(command) ? SubmitResult.ENQUEUED : SubmitResult.REJECTED_BUSY;
  }

  /** Starts the matching thread. */
  public synchronized void start() {
    if (worker != null) {
      throw new IllegalStateException("engine already started");
    }
    accepting = true;
    worker = new Thread(this::runLoop, "matching-thread");
    worker.start();
  }

  /**
   * Stops accepting commands, drains everything already enqueued, and joins the
   * matching thread. Callers should ensure producers have quiesced first so no
   * enqueued command is lost.
   *
   * @throws InterruptedException if interrupted while awaiting the matching thread
   */
  public synchronized void stop() throws InterruptedException {
    accepting = false;
    if (worker != null) {
      worker.join();
      worker = null;
    }
  }

  private void runLoop() {
    // Drain-and-stop: keep going while accepting, and once stopping keep going
    // until the queue is fully drained, so no enqueued command is lost.
    while (accepting || !ingress.isEmpty()) {
      Command command = ingress.poll();
      if (command == null) {
        Thread.onSpinWait();
        continue;
      }
      processCommand(command);
    }
  }

  /**
   * Applies one command to the book. Runs on the matching thread; exposed
   * package-private so the core logic can be exercised deterministically and
   * synchronously in tests, without threads.
   *
   * @param command the command to apply
   */
  void processCommand(Command command) {
    switch (command) {
      case SubmitOrder submit -> handleSubmit(submit);
      case SnapshotRequest request -> handleSnapshot(request);
    }
  }

  @SuppressFBWarnings(
      value = "AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE",
      justification =
          "cumulative trade counters are read and written only on the single matching thread;"
              + " the non-atomic += is safe and never contended")
  private void handleSubmit(SubmitOrder submit) {
    Order order =
        Order.create(
            submit.id(),
            submit.side(),
            submit.price(),
            submit.quantity(),
            arrivalSequence++,
            submit.accountId());
    publisher.publish(
        new OrderAccepted(
            order.id(),
            order.side(),
            order.price(),
            order.quantity(),
            order.accountId(),
            order.sequence()));

    List<Trade> trades = Matcher.match(book, order, policy, tradeSequence);
    tradeSequence += trades.size();
    for (Trade trade : trades) {
      publisher.publish(new TradeExecuted(trade));
      long notional = trade.notional();
      cumulativeCashFromBuyers += notional;
      cumulativeCashToSellers += notional;
      cumulativeAssetFromSellers += trade.quantity();
      cumulativeAssetToBuyers += trade.quantity();
    }
    publishAffectedBalances(trades);
    publisher.publish(new BookChanged(book.snapshot()));
  }

  private void handleSnapshot(SnapshotRequest request) {
    latestSnapshot.set(new StampedSnapshot(request.requestId(), buildSnapshot()));
  }

  private EngineSnapshot buildSnapshot() {
    List<Account> accounts = new ArrayList<>();
    if (ledgerView != null) {
      for (long accountId : ledgerView.accountIds()) {
        accounts.add(ledgerView.account(accountId));
      }
    }
    return new EngineSnapshot(
        book.snapshot(),
        book.restingOrders(),
        accounts,
        initialTotalCash,
        initialTotalAsset,
        cumulativeCashFromBuyers,
        cumulativeCashToSellers,
        cumulativeAssetFromSellers,
        cumulativeAssetToBuyers);
  }

  /**
   * Requests a consistent snapshot of engine and ledger state, produced on the
   * matching thread. Never touches the live book or ledger from the caller.
   *
   * @param timeout how long to wait for the snapshot
   * @return the snapshot, or empty if the request was rejected (busy/stopped) or
   *     did not complete within the timeout
   */
  public Optional<EngineSnapshot> requestSnapshot(Duration timeout) {
    long requestId = snapshotRequestIds.incrementAndGet();
    if (submit(new SnapshotRequest(requestId)) != SubmitResult.ENQUEUED) {
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

  /**
   * Publishes a balance snapshot for each distinct account that appeared in the
   * fills, read from the account view on the matching thread. Pure egress: it
   * does not affect matching or determinism, and is skipped when no account view
   * is configured.
   */
  private void publishAffectedBalances(List<Trade> trades) {
    if (ledgerView == null || trades.isEmpty()) {
      return;
    }
    Set<Long> affected = new LinkedHashSet<>();
    for (Trade trade : trades) {
      affected.add(trade.buyerAccountId());
      affected.add(trade.sellerAccountId());
    }
    for (long accountId : affected) {
      publisher.publish(
          new AccountUpdated(
              accountId, ledgerView.cashOf(accountId), ledgerView.assetOf(accountId)));
    }
  }

  /**
   * An immutable snapshot of the book. Intended for use after {@link #stop()}.
   *
   * @return the book snapshot
   */
  public BookSnapshot snapshot() {
    return book.snapshot();
  }

  /**
   * All resting orders. Intended for use after {@link #stop()}.
   *
   * @return the resting orders
   */
  public List<Order> restingOrders() {
    return book.restingOrders();
  }

  /**
   * Whether the book is crossed. Intended for use after {@link #stop()}.
   *
   * @return {@code true} if the book is crossed
   */
  public boolean isCrossed() {
    return book.isCrossed();
  }
}
