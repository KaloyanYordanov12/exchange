package dev.kaloyanyordanov.exchange.engine;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.ledger.Account;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A read model for the API, kept current from the engine's event stream. It holds
 * the latest immutable {@link BookSnapshot} and per-account balances so HTTP
 * threads can read market data <em>without ever touching the order book or ledger
 * off the matching thread</em> — the §4.4 guarantee for reads.
 *
 * <p>Updates are applied synchronously in {@link #publish(EngineEvent)} using only
 * lock-free atomics ({@link AtomicReference}, {@link ConcurrentHashMap}), so the
 * matching thread is never blocked and reads never miss an update.
 */
public final class MarketDataCache implements EventPublisher {

  private final AtomicReference<BookSnapshot> latestBook =
      new AtomicReference<>(new BookSnapshot(List.of(), List.of()));
  private final ConcurrentHashMap<Long, Account> balances = new ConcurrentHashMap<>();

  @Override
  public void publish(EngineEvent event) {
    switch (event) {
      case BookChanged bookChanged -> latestBook.set(bookChanged.snapshot());
      case AccountUpdated updated ->
          balances.put(
              updated.accountId(),
              new Account(updated.accountId(), updated.cash(), updated.asset()));
      case OrderAccepted ignored -> {
        // Not part of the read model.
      }
      case OrderRejected ignored -> {
        // Not part of the read model.
      }
      case TradeExecuted ignored -> {
        // The trade tape is the broadcaster's concern.
      }
      case CashDeposited ignored -> {
        // The resulting balance arrives as an AccountUpdated event.
      }
      case CashWithdrawn ignored -> {
        // The resulting balance arrives as an AccountUpdated event.
      }
    }
  }

  /**
   * Seeds an account's opening balances before the engine starts (funding). Safe
   * because no matching thread is running yet.
   *
   * @param accountId the account
   * @param cash      opening cash
   * @param asset     opening asset
   */
  public void seedAccount(long accountId, long cash, long asset) {
    balances.put(accountId, new Account(accountId, cash, asset));
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
   * The latest known balances for an account.
   *
   * @param accountId the account
   * @return the account snapshot, or empty if unknown
   */
  public Optional<Account> balanceOf(long accountId) {
    return Optional.ofNullable(balances.get(accountId));
  }
}
