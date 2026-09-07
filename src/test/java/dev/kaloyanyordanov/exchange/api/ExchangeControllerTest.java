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
import dev.kaloyanyordanov.exchange.ledger.Account;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExchangeControllerTest {

  private final ExchangeService service = mock(ExchangeService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeController(service)).build();
  }

  private static String body(String side, long price, long quantity) {
    return "{\"side\":\"" + side + "\",\"price\":" + price + ",\"quantity\":" + quantity + "}";
  }

  @Test
  void acceptedOrderReturns202WithOrderId() throws Exception {
    when(service.place(eq(1L), eq(Side.BUY), eq(100L), eq(5L)))
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
  void invalidOutcomeReturns400() throws Exception {
    when(service.place(anyLong(), eq(Side.BUY), anyLong(), anyLong()))
        .thenReturn(PlacementOutcome.invalid("price must be positive"));

    mockMvc
        .perform(
            post("/orders")
                .requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUY", 3L, 5L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("price must be positive"));
  }

  @Test
  void busyOutcomeReturns503() throws Exception {
    when(service.place(anyLong(), eq(Side.SELL), anyLong(), anyLong()))
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
    when(service.book())
        .thenReturn(new BookSnapshot(List.of(new PriceLevel(100L, 5L)), List.of()));

    mockMvc
        .perform(get("/book"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bids[0].price").value(100))
        .andExpect(jsonPath("$.bids[0].quantity").value(5));
  }

  @Test
  void meReturnsKnownBalance() throws Exception {
    when(service.balance(1L)).thenReturn(Optional.of(new Account(1L, 900L, 12L)));

    mockMvc
        .perform(get("/accounts/me").requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(1))
        .andExpect(jsonPath("$.cash").value(900))
        .andExpect(jsonPath("$.asset").value(12));
  }

  @Test
  void meReturnsZeroesWhenBalanceUnknown() throws Exception {
    when(service.balance(7L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/accounts/me").requestAttr(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 7L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cash").value(0))
        .andExpect(jsonPath("$.asset").value(0));
  }
}
