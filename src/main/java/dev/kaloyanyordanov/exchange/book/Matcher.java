package dev.kaloyanyordanov.exchange.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure, deterministic matching algorithm. Given a book and an incoming limit
 * order it produces the resulting trades and leaves the book updated, applying
 * strict <b>price-time priority</b>: cross the best opposite price first, then
 * the earliest order at that price, partially filling as needed and resting any
 * non-crossing remainder.
 *
 * <p>Determinism is a feature: the same book and the same command sequence yield
 * exactly the same trades and book, every time. The algorithm never rests a
 * remainder that still crosses the opposite side, so a completed match never
 * leaves the book crossed.
 */
public final class Matcher {

  private Matcher() {
    // Stateless utility.
  }

  /**
   * Matches an incoming order with no affordability limits (Phase 1 semantics).
   *
   * @param book              the book to match against and update
   * @param incoming          the incoming limit order
   * @param firstTradeSequence the sequence number for the first resulting trade
   * @return the trades produced, in execution order
   */
  public static List<Trade> match(OrderBook book, Order incoming, long firstTradeSequence) {
    return match(book, incoming, FillPolicy.UNCONSTRAINED, firstTradeSequence);
  }

  /**
   * Matches an incoming order under a fill policy (affordability caps and
   * immediate settlement), updating the book.
   *
   * @param book               the book to match against and update
   * @param incoming           the incoming limit order
   * @param policy             the fill policy (caps + settlement)
   * @param firstTradeSequence the sequence number for the first resulting trade
   * @return the trades produced, in execution order
   */
  public static List<Trade> match(
      OrderBook book, Order incoming, FillPolicy policy, long firstTradeSequence) {
    Objects.requireNonNull(book, "book");
    Objects.requireNonNull(incoming, "incoming");
    Objects.requireNonNull(policy, "policy");
    if (firstTradeSequence < 0) {
      throw new IllegalArgumentException("trade sequence must be non-negative");
    }

    List<Trade> trades = new ArrayList<>();
    Side takerSide = incoming.side();
    Side makerSide = takerSide.opposite();
    boolean takerIsBuyer = takerSide == Side.BUY;

    Order taker = incoming;
    long tradeSequence = firstTradeSequence;
    boolean rejectRemainder = false;

    while (taker.remaining() > 0) {
      Optional<Order> bestOpt = book.bestOrder(makerSide);
      if (bestOpt.isEmpty()) {
        break; // No opposite liquidity: rest the remainder.
      }
      Order maker = bestOpt.get();
      if (!crosses(takerSide, taker.price(), maker.price())) {
        break; // Best opposite no longer crosses: rest the remainder.
      }

      long tradePrice = maker.price();
      long buyerAccount = takerIsBuyer ? taker.accountId() : maker.accountId();
      long sellerAccount = takerIsBuyer ? maker.accountId() : taker.accountId();

      long desired = Math.min(taker.remaining(), maker.remaining());
      long maxBuyer = policy.maxBuyerUnits(buyerAccount, tradePrice);
      long maxSeller = policy.maxSellerUnits(sellerAccount);
      long fill = Math.min(desired, Math.min(maxBuyer, maxSeller));

      if (fill <= 0) {
        boolean buyerShort = maxBuyer <= 0;
        boolean sellerShort = maxSeller <= 0;
        boolean takerBlocked = (takerIsBuyer && buyerShort) || (!takerIsBuyer && sellerShort);
        if (takerBlocked) {
          rejectRemainder = true; // Taker can't afford to continue; do not rest (would cross).
          break;
        }
        book.removeBest(makerSide); // Maker can't settle: drop the dead order and continue.
        continue;
      }

      OrderId buyOrderId = takerIsBuyer ? taker.id() : maker.id();
      OrderId sellOrderId = takerIsBuyer ? maker.id() : taker.id();
      Trade trade =
          new Trade(
              buyOrderId, sellOrderId, tradePrice, fill, buyerAccount, sellerAccount,
              tradeSequence++);
      trades.add(trade);
      policy.onFill(trade); // Settle immediately so the next cap sees fresh balances.

      long makerRemaining = maker.remaining() - fill;
      if (makerRemaining > 0) {
        book.replaceBest(makerSide, maker.withRemaining(makerRemaining));
      } else {
        book.removeBest(makerSide);
      }
      taker = taker.withRemaining(taker.remaining() - fill);
    }

    if (taker.remaining() > 0 && !rejectRemainder) {
      book.addResting(taker);
    }
    return trades;
  }

  /**
   * Whether a taker price crosses a maker price. A buy crosses when it is willing
   * to pay at least the ask; a sell crosses when it accepts at most the bid.
   *
   * @param takerSide  the incoming order's side
   * @param takerPrice the incoming order's limit price
   * @param makerPrice the resting order's price
   * @return {@code true} if the two prices cross
   */
  private static boolean crosses(Side takerSide, long takerPrice, long makerPrice) {
    return takerSide == Side.BUY ? takerPrice >= makerPrice : takerPrice <= makerPrice;
  }
}
