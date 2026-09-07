package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Symbol;
import dev.kaloyanyordanov.exchange.engine.MarketDataCache;
import dev.kaloyanyordanov.exchange.engine.MatchingEngine;
import dev.kaloyanyordanov.exchange.engine.SubmitOrder;
import dev.kaloyanyordanov.exchange.engine.SubmitResult;
import dev.kaloyanyordanov.exchange.ledger.Account;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * The API gateway to the engine. It validates input, assigns order ids, and
 * submits to the ingress queue — it never touches the book or ledger. Reads are
 * served from the {@link MarketDataCache} (published immutable state), so no HTTP
 * thread ever accesses the book or ledger off the matching thread (§4.4).
 */
@Service
public class ExchangeService {

  private final MatchingEngine engine;
  private final MarketDataCache marketData;
  private final Symbol symbol;
  private final AtomicLong nextOrderId = new AtomicLong();

  /**
   * Creates the gateway.
   *
   * @param engine     the matching engine
   * @param marketData the read model
   * @param symbol     the traded symbol
   */
  public ExchangeService(MatchingEngine engine, MarketDataCache marketData, Symbol symbol) {
    this.engine = engine;
    this.marketData = marketData;
    this.symbol = symbol;
  }

  /**
   * Validates and submits an order to the ingress queue.
   *
   * @param accountId the owning account
   * @param side      buy or sell
   * @param price     the limit price in ticks
   * @param quantity  the quantity in units
   * @return the placement outcome (accepted / invalid / busy)
   */
  public PlacementOutcome place(long accountId, Side side, long price, long quantity) {
    if (!symbol.isValidPrice(price)) {
      return PlacementOutcome.invalid(
          "price must be positive and a multiple of tick size " + symbol.tickSize());
    }
    if (!symbol.isValidQuantity(quantity)) {
      return PlacementOutcome.invalid(
          "quantity must be positive and a multiple of lot size " + symbol.lotSize());
    }

    long orderId = nextOrderId.getAndIncrement();
    SubmitResult result =
        engine.submit(new SubmitOrder(OrderId.of(orderId), side, price, quantity, accountId));
    return switch (result) {
      case ENQUEUED -> PlacementOutcome.accepted(orderId);
      case REJECTED_BUSY, REJECTED_NOT_RUNNING -> PlacementOutcome.busy();
    };
  }

  /**
   * The latest published order-book snapshot.
   *
   * @return the book snapshot
   */
  public BookSnapshot book() {
    return marketData.book();
  }

  /**
   * The latest known balances for an account.
   *
   * @param accountId the account
   * @return the account balances, or empty if unknown
   */
  public Optional<Account> balance(long accountId) {
    return marketData.balanceOf(accountId);
  }
}
