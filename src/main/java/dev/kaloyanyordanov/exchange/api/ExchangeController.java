package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.ledger.Account;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints over the engine. Handlers submit to the ingress queue and read
 * from the published market-data cache; they never touch the book or ledger.
 */
@RestController
public class ExchangeController {

  private final ExchangeService service;

  /**
   * Creates the controller.
   *
   * @param service the API gateway
   */
  public ExchangeController(ExchangeService service) {
    this.service = service;
  }

  /**
   * Places a limit order for the authenticated account.
   *
   * @param request the order request
   * @param http    the servlet request carrying the resolved account id
   * @return 202 accepted with the order id, 400 on invalid input, or 503 when busy
   */
  @PostMapping("/orders")
  public ResponseEntity<Object> placeOrder(
      @RequestBody PlaceOrderRequest request, HttpServletRequest http) {
    Optional<Side> side = parseSide(request.side());
    if (side.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "side must be BUY or SELL"));
    }

    long accountId = accountId(http);
    PlacementOutcome outcome =
        service.place(accountId, side.get(), request.price(), request.quantity());
    return switch (outcome.status()) {
      case ACCEPTED ->
          ResponseEntity.accepted().body(new OrderResponse(outcome.orderId(), "ACCEPTED"));
      case INVALID -> ResponseEntity.badRequest().body(Map.of("error", outcome.message()));
      case BUSY ->
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
              .body(Map.of("error", outcome.message()));
    };
  }

  /**
   * The current order-book snapshot (read-only, from the published read model).
   *
   * @return the book snapshot
   */
  @GetMapping("/book")
  public BookSnapshot book() {
    return service.book();
  }

  /**
   * The authenticated account's balances.
   *
   * @param http the servlet request carrying the resolved account id
   * @return the caller's balances (zeroes if not yet known)
   */
  @GetMapping("/accounts/me")
  public BalanceResponse me(HttpServletRequest http) {
    long accountId = accountId(http);
    Account account = service.balance(accountId).orElseGet(() -> new Account(accountId, 0L, 0L));
    return new BalanceResponse(accountId, account.cash(), account.asset());
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
