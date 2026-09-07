package dev.kaloyanyordanov.exchange.realtime;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.BookChanged;
import dev.kaloyanyordanov.exchange.engine.EngineEvent;
import dev.kaloyanyordanov.exchange.engine.EventPublisher;
import dev.kaloyanyordanov.exchange.engine.TradeExecuted;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jctools.queues.MpscArrayQueue;
import tools.jackson.core.JacksonException;

/**
 * Consumes the engine's event stream and pushes throttled updates to clients.
 *
 * <p>Ingestion ({@link #publish}) runs on the matching thread and only does
 * lock-free work: coalesce the latest book snapshot (latest wins) and offer trades
 * to a bounded tape (drop-on-full). A separate scheduler thread flushes at a fixed
 * rate — one coalesced book snapshot plus a bounded batch of tape trades — so the
 * engine is never coupled to client speed, and clients receive at the cadence
 * rather than one message per book change.
 */
public final class ThrottledBroadcaster implements EventPublisher {

  /** A book snapshot message. */
  public record BookMessage(String type, List<PriceLevel> bids, List<PriceLevel> asks) {
    /** Defensive copies for immutability. */
    public BookMessage {
      bids = List.copyOf(bids);
      asks = List.copyOf(asks);
    }
  }

  /** A batch of trade-tape entries. */
  public record TradeMessage(String type, List<TapeEntry> trades) {
    /** Defensive copy for immutability. */
    public TradeMessage {
      trades = List.copyOf(trades);
    }
  }

  /** One public trade-tape entry (no account or order identity). */
  public record TapeEntry(long price, long quantity, long sequence) {}

  private final JsonSerializer serializer;
  private final int maxTradesPerFlush;
  private final long periodMillis;
  private final Set<ClientSink> clients = new CopyOnWriteArraySet<>();

  private final AtomicReference<BookSnapshot> pendingBook = new AtomicReference<>();
  private final MpscArrayQueue<Trade> tradeTape;
  private final AtomicLong serializationFailures = new AtomicLong();

  private ScheduledExecutorService scheduler;

  /**
   * Creates a broadcaster.
   *
   * @param serializer        the JSON serializer
   * @param bookHertz         book snapshot flush rate in Hz
   * @param maxTradesPerFlush the maximum trades emitted per flush
   * @param tapeCapacity      the bounded trade-tape capacity (power of two)
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "serializer is a stateless, effectively-immutable function; sharing the reference is"
              + " intentional and safe")
  public ThrottledBroadcaster(
      JsonSerializer serializer, int bookHertz, int maxTradesPerFlush, int tapeCapacity) {
    if (bookHertz <= 0) {
      throw new IllegalArgumentException("book rate must be positive: " + bookHertz);
    }
    if (maxTradesPerFlush <= 0) {
      throw new IllegalArgumentException("max trades per flush must be positive");
    }
    this.serializer = serializer;
    this.maxTradesPerFlush = maxTradesPerFlush;
    this.periodMillis = 1_000L / bookHertz;
    this.tradeTape = new MpscArrayQueue<>(tapeCapacity);
  }

  /**
   * Registers a client to receive broadcasts.
   *
   * @param client the client sink
   */
  public void register(ClientSink client) {
    clients.add(client);
  }

  /**
   * Unregisters a client.
   *
   * @param client the client sink
   */
  public void unregister(ClientSink client) {
    clients.remove(client);
  }

  /**
   * The number of registered clients.
   *
   * @return the client count
   */
  public int clientCount() {
    return clients.size();
  }

  @Override
  public void publish(EngineEvent event) {
    switch (event) {
      case BookChanged bookChanged -> pendingBook.set(bookChanged.snapshot());
      case TradeExecuted tradeExecuted -> tradeTape.offer(tradeExecuted.trade());
      default -> {
        // Order lifecycle and balance events are not broadcast market data.
      }
    }
  }

  /**
   * Emits one coalesced book snapshot (if changed) and one bounded batch of tape
   * trades (if any) to every client. Non-blocking per client.
   */
  public void flush() {
    BookSnapshot book = pendingBook.getAndSet(null);
    List<Trade> trades = drainTape();
    if (book == null && trades.isEmpty()) {
      return;
    }
    List<String> messages = new ArrayList<>(2);
    if (book != null) {
      toJson(new BookMessage("book", book.bids(), book.asks())).ifPresent(messages::add);
    }
    if (!trades.isEmpty()) {
      toJson(new TradeMessage("trades", tape(trades))).ifPresent(messages::add);
    }
    for (ClientSink client : clients) {
      for (String message : messages) {
        deliverIsolated(client, message);
      }
    }
  }

  private List<Trade> drainTape() {
    List<Trade> trades = new ArrayList<>();
    Trade trade;
    while (trades.size() < maxTradesPerFlush && (trade = tradeTape.poll()) != null) {
      trades.add(trade);
    }
    return trades;
  }

  private static List<TapeEntry> tape(List<Trade> trades) {
    List<TapeEntry> entries = new ArrayList<>(trades.size());
    for (Trade trade : trades) {
      entries.add(new TapeEntry(trade.price(), trade.quantity(), trade.sequence()));
    }
    return entries;
  }

  private static void deliverIsolated(ClientSink client, String message) {
    try {
      client.deliver(message);
    } catch (RuntimeException ignored) {
      // A misbehaving client must not stop delivery to the others.
    }
  }

  private Optional<String> toJson(Object message) {
    try {
      return Optional.of(serializer.write(message));
    } catch (JacksonException failure) {
      serializationFailures.incrementAndGet();
      return Optional.empty();
    }
  }

  /** Starts the fixed-rate flush scheduler. */
  public void start() {
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "broadcaster-flush");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleAtFixedRate(this::flush, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
  }

  /** Stops the flush scheduler. */
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  /**
   * The number of serialization failures (observability).
   *
   * @return the failure count
   */
  public long serializationFailures() {
    return serializationFailures.get();
  }
}
