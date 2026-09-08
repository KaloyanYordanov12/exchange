package dev.kaloyanyordanov.exchange.config;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Funds the configured accounts' opening <em>cash</em> through the shared cash
 * ledger once it has started, rather than mutating balances off-thread. Each opening
 * balance is a {@code genesis-*} deposit applied serially on the cash thread, so the
 * cross-account conservation law holds from the first command and section 4.4 is
 * never violated. Opening <em>asset</em> is a one-time genesis endowment seeded into
 * each pair's asset ledger before its engine starts (there is no asset-deposit path).
 */
public final class GenesisFunder {

  private final CashLedger cashLedger;
  private final List<TraderProperties> traders;
  private final Duration timeout;

  /**
   * Creates the funder.
   *
   * @param cashLedger the started shared cash ledger
   * @param traders    the configured traders and their opening cash
   * @param timeout    how long to wait for the deposits to be applied
   */
  public GenesisFunder(CashLedger cashLedger, List<TraderProperties> traders, Duration timeout) {
    this.cashLedger = Objects.requireNonNull(cashLedger, "cashLedger");
    this.traders = List.copyOf(traders);
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /**
   * Deposits every configured account's opening cash into the cash ledger, then
   * waits for the cash thread to apply them all so funding is complete before the app
   * serves traffic.
   */
  public void fund() {
    boolean anyDeposited = false;
    for (TraderProperties trader : traders) {
      if (trader.cash() <= 0) {
        continue;
      }
      if (!cashLedger.deposit(trader.accountId(), trader.cash(), "genesis-" + trader.accountId())) {
        throw new IllegalStateException(
            "genesis funding rejected for account " + trader.accountId());
      }
      anyDeposited = true;
    }
    if (anyDeposited && cashLedger.snapshot(timeout).isEmpty()) {
      throw new IllegalStateException("genesis funding did not complete within " + timeout);
    }
  }
}
