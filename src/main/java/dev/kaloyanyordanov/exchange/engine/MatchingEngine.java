package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.FillPolicy;
import dev.kaloyanyordanov.exchange.book.Matcher;
import dev.kaloyanyordanov.exchange.book.Order;
import dev.kaloyanyordanov.exchange.book.OrderBook;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.ledger.AssetLedger;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
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
 * The single-threaded matching core for <b>one pair</b>. Many producers offer
 * {@link Command}s to a bounded lock-free MPSC ring buffer; one dedicated matching
 * thread drains it and applies each command to the order book, one at a time,
 * deterministically.
 *
 * <p><b>The matching thread is the sole owner of the book, the per-pair asset
 * ledger, and the sequence counters.</b> No other thread touches them and there is
 * no lock on the book. Cash is <em>not</em> owned here: it lives in the shared
 * {@link CashLedger}, and this engine settles the cash leg of each fill by sending
 * that actor messages (never by shared-memory mutation). The buyer's affordability
 * was reserved in the cash ledger before the order entered the book, so the matcher
 * never reads cash cross-thread. Because processing is serial, the same command
 * sequence always yields the same book, asset state, and events.
 *
 * <p>The book-inspection methods ({@link #snapshot()}, {@link #restingOrders()},
 * {@link #isCrossed()}) touch the book directly and are intended for use only after
 * {@link #stop()} has joined the matching thread.
 */
public final class MatchingEngine {

  private final MpscArrayQueue<Command> ingress;
  private final int capacity;
  private final OrderBook book;
  private final FillPolicy policy;
  private final EventPublisher publisher;
  // The per-pair asset ledger (also the fill policy), read only on the matching
  // thread; null for an unconstrained (pure-matching) engine.
  private final AssetLedger assetLedger;
  // The shared cash owner; the matching thread sends it settle/release messages.
  // Null for an engine without cash settlement.
  private final CashLedger cashLedger;

  // Touched only by the matching thread (or by a caller in a single-threaded test
  // via processCommand); never shared concurrently.
  private long arrivalSequence;
  private long tradeSequence;
  private long cumulativeCashFromBuyers;
  private long cumulativeCashToSellers;
  private long cumulativeAssetFromSellers;
  private long cumulativeAssetToBuyers;
  private final long initialTotalAsset;

  // On-demand snapshot channel: the matching thread publishes here; requesters poll
  // for a stamp at or beyond their request id.
  private final AtomicReference<StampedSnapshot> latestSnapshot = new AtomicReference<>();
  private final AtomicLong snapshotRequestIds = new AtomicLong();

  private volatile boolean accepting;
  private Thread worker;

  private record StampedSnapshot(long requestId, EngineSnapshot snapshot) {}

  /**
   * Creates an engine with unconstrained matching (no ledger, no cash settlement).
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   */
  public MatchingEngine(Symbol symbol, int requestedCapacity, EventPublisher publisher) {
    this(symbol, requestedCapacity, publisher, FillPolicy.UNCONSTRAINED, null, null);
  }

  /**
   * Creates an engine with an explicit fill policy and no asset view or cash
   * settlement (used by pure-matching tests).
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   * @param policy            the fill policy applied by the matcher
   */
  public MatchingEngine(
      Symbol symbol, int requestedCapacity, EventPublisher publisher, FillPolicy policy) {
    this(symbol, requestedCapacity, publisher, policy, null, null);
  }

  /**
   * Creates a fully-wired pair engine: it caps and settles the asset leg through
   * {@code assetLedger} (also the fill policy) and settles the cash leg through the
   * shared {@code cashLedger}, publishing {@link AccountUpdated} asset snapshots
   * after settlement.
   *
   * @param symbol            the traded symbol
   * @param requestedCapacity the ingress capacity (rounded up to a power of two)
   * @param publisher         the outbound event sink
   * @param assetLedger       the per-pair asset ledger and fill policy
   * @param cashLedger        the shared cash ledger for cash settlement
   */
  public MatchingEngine(
      Symbol symbol,
      int requestedCapacity,
      EventPublisher publisher,
      AssetLedger assetLedger,
      CashLedger cashLedger) {
    this(
        symbol,
        requestedCapacity,
        publisher,
        Objects.requireNonNull(assetLedger, "assetLedger"),
        assetLedger,
        Objects.requireNonNull(cashLedger, "cashLedger"));
  }

  private MatchingEngine(
      Symbol symbol,
      int requestedCapacity,
      EventPublisher publisher,
      FillPolicy policy,
      AssetLedger assetLedger,
      CashLedger cashLedger) {
    Objects.requireNonNull(symbol, "symbol");
    if (requestedCapacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive: " + requestedCapacity);
    }
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.assetLedger = assetLedger;
    this.cashLedger = cashLedger;
    this.ingress = new MpscArrayQueue<>(requestedCapacity);
    this.capacity = ingress.capacity();
    this.book = new OrderBook(symbol);
    this.initialTotalAsset = assetLedger != null ? assetLedger.totalAsset() : 0L;
  }

  /**
   * The actual ingress capacity (the requested value rounded up to a power of two).
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
      value = {"AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE", "AT_UNSAFE_RESOURCE_ACCESS_IN_THREAD"},
      justification =
          "cumulative trade counters are read and written only on the single matching thread"
              + " (the non-atomic += is safe and never contended); the cashLedger calls are"
              + " lock-free message sends to a thread-safe single-owner actor (its own thread"
              + " applies them serially), the intended cross-thread settlement design, not an"
              + " unsafe shared-memory access")
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
    long filledQuantity = 0L;
    long filledCost = 0L;
    for (Trade trade : trades) {
      publisher.publish(new TradeExecuted(trade));
      long notional = trade.notional();
      cumulativeCashFromBuyers += notional;
      cumulativeCashToSellers += notional;
      cumulativeAssetFromSellers += trade.quantity();
      cumulativeAssetToBuyers += trade.quantity();
      filledQuantity += trade.quantity();
      filledCost += notional;
      // Settle the cash leg on the shared cash owner: the buyer's reserved cash
      // moves to the seller's available cash. The asset leg was already settled by
      // the fill policy (the asset ledger) during matching.
      if (cashLedger != null) {
        cashLedger.settle(trade.buyerAccountId(), trade.sellerAccountId(), notional);
      }
    }
    releaseBuyerPriceImprovement(order, filledQuantity, filledCost);
    publishAffectedBalances(trades);
    publisher.publish(new BookChanged(book.snapshot()));
  }

  /**
   * An aggressor buy reserved its limit cost but may fill cheaper against resting
   * asks; the price-improvement savings on the filled quantity are released back to
   * the buyer's available cash. Resting buys always fill at their own price, so
   * their savings are zero and their reservation is consumed exactly.
   */
  @SuppressFBWarnings(
      value = "AT_UNSAFE_RESOURCE_ACCESS_IN_THREAD",
      justification =
          "cashLedger.release is a lock-free message send to a thread-safe single-owner actor,"
              + " the intended cross-thread settlement design, not an unsafe shared-memory access")
  private void releaseBuyerPriceImprovement(Order order, long filledQuantity, long filledCost) {
    if (cashLedger == null || order.side() != Side.BUY || filledQuantity == 0L) {
      return;
    }
    long reservedForFilled = Math.multiplyExact(order.price(), filledQuantity);
    long savings = reservedForFilled - filledCost;
    if (savings > 0L) {
      cashLedger.release(order.accountId(), savings);
    }
  }

  private void handleSnapshot(SnapshotRequest request) {
    latestSnapshot.set(new StampedSnapshot(request.requestId(), buildSnapshot()));
  }

  private EngineSnapshot buildSnapshot() {
    List<AccountBalance> accounts = new ArrayList<>();
    if (assetLedger != null) {
      for (long accountId : assetLedger.accountIds()) {
        accounts.add(new AccountBalance(accountId, assetLedger.assetOf(accountId)));
      }
    }
    List<RestingOrder> resting = new ArrayList<>();
    for (Order order : book.restingOrders()) {
      resting.add(
          new RestingOrder(
              order.id().value(),
              order.side(),
              order.price(),
              order.quantity(),
              order.remaining(),
              order.sequence()));
    }
    return new EngineSnapshot(
        book.snapshot(),
        resting,
        accounts,
        initialTotalAsset,
        cumulativeCashFromBuyers,
        cumulativeCashToSellers,
        cumulativeAssetFromSellers,
        cumulativeAssetToBuyers);
  }

  /**
   * Requests a consistent snapshot of engine state, produced on the matching
   * thread. Never touches the live book or ledger from the caller.
   *
   * @param timeout how long to wait for the snapshot
   * @return the snapshot, or empty if the request was rejected (busy/stopped) or did
   *     not complete within the timeout
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
   * Publishes an asset snapshot for each distinct account that appeared in the
   * fills, read from the asset ledger on the matching thread. Pure egress: it does
   * not affect matching or determinism, and is skipped when no asset ledger is
   * configured.
   */
  private void publishAffectedBalances(List<Trade> trades) {
    if (assetLedger == null || trades.isEmpty()) {
      return;
    }
    Set<Long> affected = new LinkedHashSet<>();
    for (Trade trade : trades) {
      affected.add(trade.buyerAccountId());
      affected.add(trade.sellerAccountId());
    }
    for (long accountId : affected) {
      publisher.publish(new AccountUpdated(accountId, assetLedger.assetOf(accountId)));
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
