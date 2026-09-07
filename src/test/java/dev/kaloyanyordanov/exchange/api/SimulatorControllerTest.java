package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.any;
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
import dev.kaloyanyordanov.exchange.sim.LatencySummary;
import dev.kaloyanyordanov.exchange.sim.LoadSimulator;
import dev.kaloyanyordanov.exchange.sim.MetricsSnapshot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SimulatorControllerTest {

  private final LoadSimulator simulator = mock(LoadSimulator.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ExchangeProperties properties =
        new ExchangeProperties(
            new SymbolProperties("BTC", "USD", 1L, 1L),
            1024,
            List.of(
                new TraderProperties(1L, "h", 0L, 0L), new TraderProperties(2L, "h", 0L, 0L)),
            null);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SimulatorController(simulator, properties)).build();
  }

  private static String startBody(int traderCount) {
    return "{\"traderCount\":" + traderCount
        + ",\"ordersPerTrader\":10,\"orderRatePerSecond\":0,\"durationMillis\":1000,"
        + "\"midPrice\":100,\"priceSpreadTicks\":5,\"minQuantity\":1,\"maxQuantity\":5,"
        + "\"randomSeed\":7,\"maxLatencySamples\":1000}";
  }

  @Test
  void startAcceptsValidRun() throws Exception {
    when(simulator.metrics()).thenReturn(MetricsSnapshot.EMPTY);
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(4)))
        .andExpect(status().isAccepted());
    verify(simulator).start(any());
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
        .when(simulator)
        .start(any());
    mockMvc
        .perform(
            post("/admin/simulator/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(4)))
        .andExpect(status().isConflict());
  }

  @Test
  void stopReturnsMetrics() throws Exception {
    when(simulator.metrics()).thenReturn(MetricsSnapshot.EMPTY);
    mockMvc.perform(post("/admin/simulator/stop")).andExpect(status().isOk());
    verify(simulator).stop();
  }

  @Test
  void metricsReturnsSnapshot() throws Exception {
    when(simulator.metrics())
        .thenReturn(
            new MetricsSnapshot(
                true, 100L, 90L, 10L, 40L, 500L, 180.0,
                new LatencySummary(90L, 1L, 2L, 3L, 4L)));
    mockMvc
        .perform(get("/admin/simulator/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submitted").value(100))
        .andExpect(jsonPath("$.accepted").value(90))
        .andExpect(jsonPath("$.rejected").value(10))
        .andExpect(jsonPath("$.latency.p99Nanos").value(3));
  }
}
