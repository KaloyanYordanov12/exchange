package dev.kaloyanyordanov.exchange.ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cash state — every account's available and reserved balance plus the running
 * deposited/withdrawn totals — and the serial logic that mutates it. Like
 * {@link Ledger}, it is a plain object with no thread of its own: {@link CashLedger}
 * owns the one thread and calls these methods on it, so the check-then-commit here
 * is atomic by that single ownership, exactly as the matching core is.
 */
final class CashBook {

  /** Mutable per-account cash; mutated only via {@link CashLedger}'s one thread. */
  private static final class Position {
    private long available;
    private long reserved;
  }

  private final Map<Long, Position> positions = new HashMap<>();
  private long cumulativeDeposited;
  private long cumulativeWithdrawn;

  /** Credits an account's available cash (a deposit). */
  void deposit(long account, long amount) {
    Position position = position(account);
    position.available = Math.addExact(position.available, amount);
    cumulativeDeposited = Math.addExact(cumulativeDeposited, amount);
  }

  /**
   * Debits an account's available cash if it can cover the amount; reserved cash is
   * never touched.
   *
   * @return {@code true} if debited, {@code false} if insufficient available cash
   */
  boolean withdraw(long account, long amount) {
    Position position = positions.get(account);
    if (position == null || position.available < amount) {
      return false;
    }
    position.available = Math.subtractExact(position.available, amount);
    cumulativeWithdrawn = Math.addExact(cumulativeWithdrawn, amount);
    return true;
  }

  /**
   * Moves available cash into reserved (buying-power hold) if it can cover the
   * amount.
   *
   * @return {@code true} if reserved, {@code false} if insufficient available cash
   */
  boolean reserve(long account, long amount) {
    Position position = positions.get(account);
    if (position == null || position.available < amount) {
      return false;
    }
    position.available = Math.subtractExact(position.available, amount);
    position.reserved = Math.addExact(position.reserved, amount);
    return true;
  }

  /** Moves reserved cash back to available (unspent buying power). */
  void release(long account, long amount) {
    Position position = position(account);
    position.reserved = Math.subtractExact(position.reserved, amount);
    position.available = Math.addExact(position.available, amount);
  }

  /** Moves cash from the buyer's reserved balance to the seller's available balance. */
  void settle(long buyerAccount, long sellerAccount, long amount) {
    Position buyer = position(buyerAccount);
    Position seller = position(sellerAccount);
    buyer.reserved = Math.subtractExact(buyer.reserved, amount);
    seller.available = Math.addExact(seller.available, amount);
  }

  private Position position(long account) {
    return positions.computeIfAbsent(account, id -> new Position());
  }

  /** The available cash of an account (zero if unknown). */
  long availableOf(long account) {
    Position position = positions.get(account);
    return position == null ? 0L : position.available;
  }

  /** An immutable snapshot of the whole cash ledger. */
  CashSnapshot snapshot() {
    List<CashAccount> accounts = new ArrayList<>(positions.size());
    for (Map.Entry<Long, Position> entry : positions.entrySet()) {
      accounts.add(
          new CashAccount(entry.getKey(), entry.getValue().available, entry.getValue().reserved));
    }
    return new CashSnapshot(accounts, cumulativeDeposited, cumulativeWithdrawn);
  }
}
