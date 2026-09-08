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
import dev.kaloyanyordanov.exchange.ledger.CashAccount;
import dev.kaloyanyordanov.exchange.ledger.CashLedger;
import dev.kaloyanyordanov.exchange.ledger.ReservationOutcome;
import dev.kaloyanyordanov.exchange.payment.FundingResult;
import dev.kaloyanyordanov.exchange.payment.PaymentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The API gateway to one pair's engine. It validates input, reserves a buy's cash in
 * the shared cash ledger before the order enters the book, assigns order ids, and
 * submits to the ingress queue - it never touches the book or asset ledger. Reads are
 * served from the {@link MarketDataCache} (published immutable state) and the cash
 * ledger's snapshot, so no HTTP thread ever accesses a book or ledger off its owning
 * thread (section 4.4).
 */
public class ExchangeService {

  private final MatchingEngine engine;
  private final MarketDataCache marketData;
  private final CashLedger cashLedger;
  private final PaymentService paymentService;
  private final Symbol symbol;
  private final Duration ledgerTimeout;
  private final AtomicLong nextOrderId;

  /**
   * Creates the gateway.
   *
   * @param engine         the matching engine
   * @param marketData     the per-pair read model
   * @param cashLedger     the shared cash ledger (reservations and balances)
   * @param paymentService the deposit/withdrawal orchestrator
   * @param symbol         the traded symbol
   * @param ledgerTimeout  how long to wait for a reservation or balance snapshot
   * @param orderIdBase    the first order id assigned by this gateway
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "engine, cash ledger, and payment service are shared singleton services injected by"
              + " the container; storing the shared references is the intended design")
  public ExchangeService(
      MatchingEngine engine,
      MarketDataCache marketData,
      CashLedger cashLedger,
      PaymentService paymentService,
      Symbol symbol,
      Duration ledgerTimeout,
      long orderIdBase) {
    this.engine = engine;
    this.marketData = marketData;
    this.cashLedger = cashLedger;
    this.paymentService = paymentService;
    this.symbol = symbol;
    this.ledgerTimeout = ledgerTimeout;
    this.nextOrderId = new AtomicLong(orderIdBase);
  }

  /**
   * Validates, reserves buying power for a buy, and submits an order to the ingress
   * queue.
   *
   * @param accountId the owning account
   * @param side      buy or sell
   * @param price     the limit price in ticks
   * @param quantity  the quantity in units
   * @return the placement outcome (accepted / invalid / rejected / busy)
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

    long cost = 0L;
    if (side == Side.BUY) {
      cost = Math.multiplyExact(price, quantity);
      ReservationOutcome reservation = cashLedger.reserve(accountId, cost, ledgerTimeout);
      if (reservation == ReservationOutcome.INSUFFICIENT_FUNDS) {
        return PlacementOutcome.rejected("insufficient cash to fund the order");
      }
      if (reservation != ReservationOutcome.RESERVED) {
        return PlacementOutcome.busy();
      }
    }

    long orderId = nextOrderId.getAndIncrement();
    SubmitResult result =
        engine.submit(new SubmitOrder(OrderId.of(orderId), side, price, quantity, accountId));
    if (result != SubmitResult.ENQUEUED) {
      if (side == Side.BUY) {
        // The order never entered the book; give the reserved cash back.
        cashLedger.release(accountId, cost);
      }
      return PlacementOutcome.busy();
    }
    return PlacementOutcome.accepted(orderId);
  }

  /**
   * Deposits cash into an account via the payment provider and the shared cash
   * ledger.
   *
   * @param accountId the account to credit
   * @param amount    the amount in scaled integer quote units; must be positive
   * @return the funding result
   */
  public FundingResult deposit(long accountId, long amount) {
    return paymentService.deposit(accountId, amount);
  }

  /**
   * Withdraws cash from an account via the shared cash ledger (the debit is atomic on
   * the cash thread and touches only available cash) and the payment provider.
   *
   * @param accountId the account to debit
   * @param amount    the amount in scaled integer quote units; must be positive
   * @return the funding result
   */
  public FundingResult withdraw(long accountId, long amount) {
    return paymentService.withdraw(accountId, amount);
  }

  /**
   * The latest published order-book snapshot for this pair.
   *
   * @return the book snapshot
   */
  public BookSnapshot book() {
    return marketData.book();
  }

  /**
   * An account's balance: its shared cash (available plus reserved) and its asset
   * holding in this pair.
   *
   * @param accountId the account
   * @return the balance (zeroes for anything unknown)
   */
  public Account balance(long accountId) {
    long cash =
        cashLedger.snapshot(ledgerTimeout)
            .map(
                snapshot ->
                    snapshot.accounts().stream()
                        .filter(account -> account.accountId() == accountId)
                        .findFirst()
                        .map(CashAccount::total)
                        .orElse(0L))
            .orElse(0L);
    long asset = marketData.assetOf(accountId).orElse(0L);
    return new Account(accountId, cash, asset);
  }
}
