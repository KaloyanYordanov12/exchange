package dev.kaloyanyordanov.exchange.api;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests over the running app context: the API-key auth filter, the
 * controllers, and the async engine wired together. Excluded from PIT (slow full
 * context); the fast unit/standalone tests carry mutation coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExchangeApiIntegrationTest {

  private static final String ALICE_KEY = "demo-alice-key";
  private static final String BOB_KEY = "demo-bob-key";
  private static final String ADMIN_KEY = "demo-admin-key";

  @Autowired private MockMvc mockMvc;

  private static String body(String side, long price, long quantity) {
    return "{\"side\":\"" + side + "\",\"price\":" + price + ",\"quantity\":" + quantity + "}";
  }

  @Test
  void unauthenticatedOrderIsRejected401() throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUY", 100L, 5L)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void bookIsPublic() throws Exception {
    mockMvc.perform(get("/book")).andExpect(status().isOk()).andExpect(jsonPath("$.bids").exists());
  }

  @Test
  void authenticatedBalanceEndpointReturnsThisAccount() throws Exception {
    // Balances may reflect trades from other tests in the shared context, so this
    // asserts only identity and shape; exact seeding is covered by unit tests.
    mockMvc
        .perform(get("/accounts/me").header(ApiKeyAuthFilter.API_KEY_HEADER, ALICE_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(1))
        .andExpect(jsonPath("$.cash").isNumber())
        .andExpect(jsonPath("$.asset").isNumber());
  }

  @Test
  void adminSurfaceRequiresTheAdminKey() throws Exception {
    mockMvc.perform(get("/admin/simulator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanDriveTheSimulatorAndReadRealMetrics() throws Exception {
    String start =
        "{\"traderCount\":2,\"ordersPerTrader\":10,\"orderRatePerSecond\":0,"
            + "\"durationMillis\":10000,\"midPrice\":100,\"priceSpreadTicks\":5,"
            + "\"minQuantity\":1,\"maxQuantity\":3,\"randomSeed\":1,\"maxLatencySamples\":100000}";
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(start))
        .andExpect(status().isAccepted());

    // The simulator drove the real engine; metrics show real, measured activity.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                mockMvc
                    .perform(
                        get("/admin/simulator/metrics")
                            .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accepted").value(20)));
  }

  @Test
  void ordersFlowThroughToSettlementAndAreReadableFromTheCache() throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, BOB_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SELL", 100L, 10L)))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(
            post("/orders")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, ALICE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("BUY", 100L, 10L)))
        .andExpect(status().isAccepted());

    // Settlement is async on the matching thread; the read model catches up.
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                mockMvc
                    .perform(get("/accounts/me").header(ApiKeyAuthFilter.API_KEY_HEADER, ALICE_KEY))
                    .andExpect(jsonPath("$.asset").value(1_010))
                    .andExpect(jsonPath("$.cash").value(99_999_000L)));
  }
}
