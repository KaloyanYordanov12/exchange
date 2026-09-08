package dev.kaloyanyordanov.exchange.api;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end tests over the running multi-pair context: the API-key auth filter, the
 * pair-routed controllers, and the async engines wired together. Trading uses the
 * DOGE-USD pair (tick 1) so small round prices are valid and affordable. Excluded
 * from PIT (slow full context); the fast unit/standalone tests carry mutation
 * coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExchangeApiIntegrationTest {

  private static final String ALICE_KEY = "demo-alice-key";
  private static final String BOB_KEY = "demo-bob-key";
  private static final String ADMIN_KEY = "demo-admin-key";
  private static final String PAIR = "DOGE-USD";
  // The simulator runs on a different pair so its (shared) cash and (per-pair) asset
  // activity cannot perturb the settlement test's DOGE asset assertion.
  private static final String SIM_PAIR = "XRP-USD";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper mapper;

  private static String order(String side, long price, long quantity) {
    return "{\"pair\":\"" + PAIR + "\",\"side\":\"" + side + "\",\"price\":" + price
        + ",\"quantity\":" + quantity + "}";
  }

  @Test
  void unauthenticatedOrderIsRejected401() throws Exception {
    mockMvc
        .perform(
            post("/orders").contentType(MediaType.APPLICATION_JSON).content(order("BUY", 100L, 5L)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void pairsAndBookArePublic() throws Exception {
    mockMvc
        .perform(get("/pairs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pairId").exists());
    mockMvc
        .perform(get("/book").param("pair", PAIR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bids").exists());
  }

  @Test
  void authenticatedBalanceEndpointReturnsCashAndHoldings() throws Exception {
    mockMvc
        .perform(get("/accounts/me").header(ApiKeyAuthFilter.API_KEY_HEADER, ALICE_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(1))
        .andExpect(jsonPath("$.cash").isNumber())
        .andExpect(jsonPath("$.holdings").isArray());
  }

  @Test
  void invariantPanelIsPublicAndGreen() throws Exception {
    mockMvc
        .perform(get("/invariants/check").param("pair", PAIR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.allPassed").value(true));
  }

  @Test
  void adminSurfaceRequiresTheAdminKey() throws Exception {
    mockMvc
        .perform(get("/admin/simulator/metrics").param("pair", PAIR))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanDriveTheSimulatorAndReadRealMetrics() throws Exception {
    String start =
        "{\"pair\":\"" + SIM_PAIR + "\",\"traderCount\":2,\"ordersPerTrader\":10,"
            + "\"orderRatePerSecond\":0,\"durationMillis\":10000,\"midPrice\":100,"
            + "\"priceSpreadTicks\":5,\"minQuantity\":1,\"maxQuantity\":3,\"randomSeed\":1,"
            + "\"maxLatencySamples\":100000}";
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
                            .param("pair", SIM_PAIR)
                            .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accepted").value(20)));
  }

  @Test
  void ordersFlowThroughToSettlement() throws Exception {
    long assetBefore = holding(ALICE_KEY);

    placeOrder(BOB_KEY, "SELL", 100L, 10L);
    placeOrder(ALICE_KEY, "BUY", 100L, 10L);

    // Settlement is async; the read model catches up. Alice's DOGE asset rises by 10
    // (the DOGE simulator never runs, so this delta is exact regardless of other
    // tests). Cash is shared across pairs, so it is not asserted here.
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                org.assertj.core.api.Assertions.assertThat(holding(ALICE_KEY))
                    .isEqualTo(assetBefore + 10L));
  }

  private void placeOrder(String key, String side, long price, long quantity) throws Exception {
    mockMvc
        .perform(
            post("/orders")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(order(side, price, quantity)))
        .andExpect(status().isAccepted());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> balance(String key) throws Exception {
    String json =
        mockMvc
            .perform(get("/accounts/me").header(ApiKeyAuthFilter.API_KEY_HEADER, key))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readValue(json, Map.class);
  }

  @SuppressWarnings("unchecked")
  private long holding(String key) throws Exception {
    List<Map<String, Object>> holdings = (List<Map<String, Object>>) balance(key).get("holdings");
    return holdings.stream()
        .filter(h -> PAIR.equals(h.get("pairId")))
        .map(h -> ((Number) h.get("asset")).longValue())
        .findFirst()
        .orElse(0L);
  }
}
