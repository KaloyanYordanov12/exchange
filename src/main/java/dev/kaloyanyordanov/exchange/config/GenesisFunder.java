package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Funds the configured accounts' opening <em>cash</em> through the audited deposit
 * path once the engine has started, rather than mutating the ledger off-thread.
 * Each opening balance is a {@code genesis-*} deposit applied serially on the
 * matching thread (a {@code CashDeposited} event, recorded like any other), so the
 * deposit-aware conservation law holds from the first command and §4.4 is never
 * violated. Opening <em>asset</em> is a one-time genesis endowment seeded directly
 * before the engine starts — there is no asset-deposit path.
 */
public final class GenesisFunder {

  private final MatchingEngine engine;
  private final List<TraderProperties> traders;
  private final Duration timeout;

  /**
   * Creates the funder.
   *
   * @param engine  the started matching engine
   * @param traders the configured traders and their opening cash
   * @param timeout how long to wait for the deposits to be applied
   */
  public GenesisFunder(
      MatchingEngine engine, List<TraderProperties> traders, Duration timeout) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.traders = List.copyOf(traders);
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /**
   * Deposits every configured account's opening cash through the engine, then waits
   * for the matching thread to apply them all so funding is complete before the app
   * serves traffic.
   */
  public void fund() {
    boolean anyDeposited = false;
    for (TraderProperties trader : traders) {
      if (trader.cash() <= 0) {
        continue;
      }
      SubmitResult result =
          engine.deposit(trader.accountId(), trader.cash(), "genesis-" + trader.accountId());
      if (result != SubmitResult.ENQUEUED) {
        throw new IllegalStateException(
            "genesis funding rejected for account " + trader.accountId() + ": " + result);
      }
      anyDeposited = true;
    }
    if (anyDeposited && engine.requestSnapshot(timeout).isEmpty()) {
      throw new IllegalStateException("genesis funding did not complete within " + timeout);
    }
  }
}
