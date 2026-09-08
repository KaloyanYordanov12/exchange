package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.PriceLevel;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.payment.FundingResult;
import dev.kaloyanyordanov.exchange.payment.FundingStatus;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry.AccountBalances;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry.AssetHolding;
import dev.kaloyanyordanov.exchange.platform.PairInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExchangeControllerTest {

  private final ExchangeRegistry registry = mock(ExchangeRegistry.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(registry.hasPair("BTC-USD")).thenReturn(true);
    mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeController(registry)).build();
  }

  private static String body(String side, long price, long quantity) {
    return "{\"pair\":\"BTC-USD\",\"side\":\"" + side + "\",\"price\":" + price
        + ",\"quantity\":" + quantity + "}";
  }

  @Test
  void listsPairs() throws Exception {
    when(registry.pairs())
        .thenReturn(List.of(new PairInfo("BTC-USD", "BTC", "USD", 10_000L, 1L, 40_000_000_000L)));
    mockMvc
        .perform(get("/pairs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pairId").value("BTC-USD"))
        .andExpect(jsonPath("$[0].referencePrice").value(40_000_000_000L));
  }

  @Test
  void acceptedOrderReturns202WithOrderId() throws Exception {
    when(registry.place(eq("BTC-USD"), eq(1L), eq(Side.BUY), eq(100L), eq(5L)))
        .thenReturn(PlacementOutcome.accepted(42L));

    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUY", 100L, 5L)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.orderId").value(42))
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void unknownPairOrderReturns404() throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pair\":\"NOPE-USD\",\"side\":\"BUY\",\"price\":100,\"quantity\":5}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidSideReturns400() throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SIDEWAYS", 100L, 5L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectedOutcomeReturns422() throws Exception {
    when(registry.place(eq("BTC-USD"), anyLong(), eq(Side.BUY), anyLong(), anyLong()))
        .thenReturn(PlacementOutcome.rejected("insufficient cash to fund the order"));

    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUY", 100L, 5L)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("insufficient cash to fund the order"));
  }

  @Test
  void busyOutcomeReturns503() throws Exception {
    when(registry.place(eq("BTC-USD"), anyLong(), eq(Side.SELL), anyLong(), anyLong()))
        .thenReturn(PlacementOutcome.busy());

    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SELL", 100L, 5L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("system busy"));
  }

  @Test
  void bookReturnsSnapshot() throws Exception {
    when(registry.book("BTC-USD"))
        .thenReturn(new BookSnapshot(List.of(new PriceLevel(100L, 5L)), List.of()));

    mockMvc
        .perform(get("/book").param("pair", "BTC-USD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bids[0].price").value(100))
        .andExpect(jsonPath("$.bids[0].quantity").value(5));
  }

  @Test
  void bookForUnknownPairReturns404() throws Exception {
    mockMvc.perform(get("/book").param("pair", "NOPE-USD")).andExpect(status().isNotFound());
  }

  @Test
  void meReturnsCashAndPerPairAssets() throws Exception {
    when(registry.balance(1L))
        .thenReturn(new AccountBalances(1L, 900L, List.of(new AssetHolding("BTC-USD", 12L))));

    mockMvc
        .perform(get("/accounts/me").requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(1))
        .andExpect(jsonPath("$.cash").value(900))
        .andExpect(jsonPath("$.holdings[0].pairId").value("BTC-USD"))
        .andExpect(jsonPath("$.holdings[0].asset").value(12));
  }

  @Test
  void acceptedDepositReturns202() throws Exception {
    when(registry.deposit(1L, 500L))
        .thenReturn(new FundingResult(FundingStatus.ACCEPTED, "demo-deposit-1"));

    mockMvc
        .perform(
            post("/accounts/deposit")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":500}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.amount").value(500))
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.reference").value("demo-deposit-1"));
  }

  @Test
  void nonPositiveDepositReturns400() throws Exception {
    mockMvc
        .perform(
            post("/accounts/deposit")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("amount must be positive"));
  }

  @Test
  void appliedWithdrawalReturns200() throws Exception {
    when(registry.withdraw(1L, 200L))
        .thenReturn(new FundingResult(FundingStatus.APPLIED, "demo-withdrawal-1"));

    mockMvc
        .perform(
            post("/accounts/withdraw")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPLIED"));
  }

  @Test
  void insufficientFundsWithdrawalReturns422() throws Exception {
    when(registry.withdraw(1L, 9_999L))
        .thenReturn(new FundingResult(FundingStatus.INSUFFICIENT_FUNDS, "demo-withdrawal-2"));

    mockMvc
        .perform(
            post("/accounts/withdraw")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9999}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("INSUFFICIENT_FUNDS"));
  }
}
