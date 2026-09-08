package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A per-pair read model for the API, kept current from one engine's event stream.
 * It holds the latest immutable {@link BookSnapshot} and per-account <b>asset</b>
 * holdings for this pair, so HTTP threads can read market data <em>without ever
 * touching the order book or ledger off the matching thread</em> (the §4.4 guarantee
 * for reads). Cash is not held here: it is shared across pairs and read from the
 * cash ledger; a balance response combines this pair's asset with that shared cash.
 *
 * <p>Updates are applied synchronously in {@link #publish(EngineEvent)} using only
 * lock-free atomics ({@link AtomicReference}, {@link ConcurrentHashMap}), so the
 * matching thread is never blocked and reads never miss an update.
 */
public final class MarketDataCache implements EventPublisher {

  private final AtomicReference<BookSnapshot> latestBook =
      new AtomicReference<>(new BookSnapshot(List.of(), List.of()));
  private final ConcurrentHashMap<Long, Long> assets = new ConcurrentHashMap<>();

  @Override
  public void publish(EngineEvent event) {
    switch (event) {
      case BookChanged bookChanged -> latestBook.set(bookChanged.snapshot());
      case AccountUpdated updated -> assets.put(updated.accountId(), updated.asset());
      case OrderAccepted ignored -> {
        // Not part of the read model.
      }
      case OrderRejected ignored -> {
        // Not part of the read model.
      }
      case TradeExecuted ignored -> {
        // The trade tape is the broadcaster's concern.
      }
    }
  }

  /**
   * Seeds an account's opening asset before the engine starts (genesis endowment).
   * Safe because no matching thread is running yet.
   *
   * @param accountId the account
   * @param asset     opening asset for this pair
   */
  public void seedAsset(long accountId, long asset) {
    assets.put(accountId, asset);
  }

  /**
   * The latest published book snapshot (immutable; never the live book).
   *
   * @return the latest book snapshot
   */
  public BookSnapshot book() {
    return latestBook.get();
  }

  /**
   * The latest known asset holding for an account in this pair.
   *
   * @param accountId the account
   * @return the asset holding, or empty if unknown
   */
  public Optional<Long> assetOf(long accountId) {
    return Optional.ofNullable(assets.get(accountId));
  }
}
