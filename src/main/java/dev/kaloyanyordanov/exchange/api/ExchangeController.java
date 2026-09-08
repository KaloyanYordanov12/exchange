package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.payment.FundingResult;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.platform.PairInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints over the multi-pair platform. Handlers route by pair to the right
 * engine's gateway (submitting to that engine's ingress and reading its published
 * read model); they never touch a book or ledger off its owning thread. The pairs
 * and book endpoints are public market data; orders and accounts require the key.
 */
@RestController
public class ExchangeController {

  private final ExchangeRegistry registry;

  /**
   * Creates the controller.
   *
   * @param registry the pair registry / routing hub
   */
  public ExchangeController(ExchangeRegistry registry) {
    this.registry = registry;
  }

  /**
   * The tradable pairs (public markets listing).
   *
   * @return the pair descriptors
   */
  @GetMapping("/pairs")
  public List<PairInfo> pairs() {
    return registry.pairs();
  }

  /**
   * Places a limit order for the authenticated account on the requested pair.
   *
   * @param request the order request (pair, side, price, quantity)
   * @param http    the servlet request carrying the resolved account id
   * @return 202 accepted with the order id; 400 invalid; 404 unknown pair; 422
   *     insufficient cash; 503 busy
   */
  @PostMapping("/orders")
  public ResponseEntity<Object> placeOrder(
      @RequestBody PlaceOrderRequest request, HttpServletRequest http) {
    if (request.pair() == null || !registry.hasPair(request.pair())) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
    Optional<Side> side = parseSide(request.side());
    if (side.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "side must be BUY or SELL"));
    }
    long accountId = accountId(http);
    PlacementOutcome outcome =
        registry.place(request.pair(), accountId, side.get(), request.price(), request.quantity());
    return switch (outcome.status()) {
      case ACCEPTED ->
          ResponseEntity.accepted().body(new OrderResponse(outcome.orderId(), "ACCEPTED"));
      case INVALID -> ResponseEntity.badRequest().body(Map.of("error", outcome.message()));
      case REJECTED ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
              .body(Map.of("error", outcome.message()));
      case BUSY ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .body(Map.of("error", outcome.message()));
    };
  }

  /**
   * The current order-book snapshot for a pair (public read model).
   *
   * @param pair the pair id
   * @return the book snapshot, or 404 for an unknown pair
   */
  @GetMapping("/book")
  public ResponseEntity<Object> book(@RequestParam String pair) {
    if (!registry.hasPair(pair)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown pair"));
    }
    BookSnapshot snapshot = registry.book(pair);
    return ResponseEntity.ok(snapshot);
  }

  /**
   * The authenticated account's balances: shared cash plus per-pair asset holdings.
   *
   * @param http the servlet request carrying the resolved account id
   * @return the caller's balances
   */
  @GetMapping("/accounts/me")
  public ExchangeRegistry.AccountBalances me(HttpServletRequest http) {
    return registry.balance(accountId(http));
  }

  /**
   * Deposits cash into the authenticated account (pair-independent).
   *
   * @param request the amount to deposit
   * @param http    the servlet request carrying the resolved account id
   * @return 202 accepted once authorized and enqueued, or 400 on a non-positive amount
   */
  @PostMapping("/accounts/deposit")
  public ResponseEntity<Object> deposit(
      @RequestBody FundingRequest request, HttpServletRequest http) {
    if (request.amount() <= 0) {
      return ResponseEntity.badRequest().body(Map.of("error", "amount must be positive"));
    }
    long accountId = accountId(http);
    return fundingResponse(
        accountId, request.amount(), registry.deposit(accountId, request.amount()));
  }

  /**
   * Withdraws cash from the authenticated account (pair-independent). The debit is
   * atomic on the cash thread and touches only available cash.
   *
   * @param request the amount to withdraw
   * @param http    the servlet request carrying the resolved account id
   * @return 200 success, 422 insufficient funds, or 400 on a non-positive amount
   */
  @PostMapping("/accounts/withdraw")
  public ResponseEntity<Object> withdraw(
      @RequestBody FundingRequest request, HttpServletRequest http) {
    if (request.amount() <= 0) {
      return ResponseEntity.badRequest().body(Map.of("error", "amount must be positive"));
    }
    long accountId = accountId(http);
    return fundingResponse(
        accountId, request.amount(), registry.withdraw(accountId, request.amount()));
  }

  private static ResponseEntity<Object> fundingResponse(
      long accountId, long amount, FundingResult result) {
    HttpStatus status =
        switch (result.status()) {
          case ACCEPTED -> HttpStatus.ACCEPTED;
          case APPLIED -> HttpStatus.OK;
          case INSUFFICIENT_FUNDS -> HttpStatus.UNPROCESSABLE_ENTITY;
          case PROVIDER_DECLINED -> HttpStatus.BAD_GATEWAY;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    return ResponseEntity.status(status)
        .body(new FundingResponse(accountId, amount, result.status().name(), result.reference()));
  }

  private static long accountId(HttpServletRequest http) {
    return (Long) http.getAttribute(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE);
  }

  private static Optional<Side> parseSide(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Side.valueOf(raw.toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException unknownSide) {
      return Optional.empty();
    }
  }
}
