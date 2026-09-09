package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.SymbolProperties;
import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import dev.kaloyanyordanov.exchange.sim.LatencySummary;
import dev.kaloyanyordanov.exchange.sim.MetricsSnapshot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SimulatorControllerTest {

  private final ExchangeRegistry registry = mock(ExchangeRegistry.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(registry.hasPair("BTC-USD")).thenReturn(true);
    when(registry.simulatorMetrics("BTC-USD")).thenReturn(MetricsSnapshot.EMPTY);
    ExchangeProperties properties =
        new ExchangeProperties(
            new SymbolProperties("BTC", "USD", 1L, 1L),
            null,
            1024,
            List.of(
                new TraderProperties(1L, "h", 0L, 0L), new TraderProperties(2L, "h", 0L, 0L)),
            null);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SimulatorController(registry, properties)).build();
  }

  private static String startBody(int traderCount) {
    return "{\"pair\":\"BTC-USD\",\"traderCount\":" + traderCount
        + ",\"ordersPerTrader\":10,\"orderRatePerSecond\":0,\"durationMillis\":1000,"
        + "\"midPrice\":100,\"priceSpreadTicks\":5,\"minQuantity\":1,\"maxQuantity\":5,"
        + "\"randomSeed\":7,\"maxLatencySamples\":1000,\"minThinkMillis\":0,"
        + "\"maxThinkMillis\":0,\"aggression\":0.3}";
  }

  @Test
  void startAcceptsValidRun() throws Exception {
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(4)))
        .andExpect(status().isAccepted());
    verify(registry).startSimulator(eq("BTC-USD"), any());
  }

  @Test
  void startForUnknownPairReturns404() throws Exception {
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(4).replace("BTC-USD", "NOPE-USD")))
        .andExpect(status().isNotFound());
  }

  @Test
  void startWithInvalidConfigIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(0))) // traderCount 0 -> invalid
        .andExpect(status().isBadRequest());
  }

  @Test
  void startWhileRunningIsConflict() throws Exception {
    doThrow(new IllegalStateException("a simulation is already running"))
        .when(registry)
        .startSimulator(eq("BTC-USD"), any());
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(4)))
        .andExpect(status().isConflict());
  }

  @Test
  void stopReturnsMetrics() throws Exception {
    mockMvc
        .perform(post("/admin/simulator/stop").param("pair", "BTC-USD"))
        .andExpect(status().isOk());
    verify(registry).stopSimulator("BTC-USD");
  }

  @Test
  void metricsReturnsSnapshot() throws Exception {
    when(registry.simulatorMetrics("BTC-USD"))
        .thenReturn(
            new MetricsSnapshot(
                true, 100L, 90L, 10L, 40L, 500L, 180.0,
                new LatencySummary(90L, 1L, 2L, 3L, 4L)));
    mockMvc
        .perform(get("/admin/simulator/metrics").param("pair", "BTC-USD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submitted").value(100))
        .andExpect(jsonPath("$.accepted").value(90))
        .andExpect(jsonPath("$.rejected").value(10))
        .andExpect(jsonPath("$.latency.p99Nanos").value(3));
  }
}
