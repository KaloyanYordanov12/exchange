package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.exchange.candle.CandlePoint;
import dev.kaloyanyordanov.exchange.candle.CandleSeries;
import dev.kaloyanyordanov.exchange.candle.CandleService;
import dev.kaloyanyordanov.exchange.candle.Timeframe;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CandleControllerTest {

  private final CandleService candleService = mock(CandleService.class);
  private final ExchangeRegistry registry = mock(ExchangeRegistry.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(registry.hasPair("BTC-USD")).thenReturn(true);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new CandleController(candleService, registry)).build();
  }

  @Test
  void returnsCandlesForPairAndTimeframe() throws Exception {
    CandleSeries series =
        new CandleSeries(
            "BTC-USD", "1m", 1_700_000_060L,
            List.of(new CandlePoint(1_700_000_000L, 100L, 110L, 90L, 105L, 12L, "REAL")));
    when(candleService.series(eq("BTC-USD"), eq(Timeframe.M1), any(), any())).thenReturn(series);

    mockMvc
        .perform(get("/candles").param("pair", "BTC-USD").param("timeframe", "1m"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pair").value("BTC-USD"))
        .andExpect(jsonPath("$.realBoundary").value(1_700_000_060L))
        .andExpect(jsonPath("$.candles[0].close").value(105))
        .andExpect(jsonPath("$.candles[0].source").value("REAL"));
  }

  @Test
  void oneYearViewIsServedAsDailyCandles() throws Exception {
    when(candleService.series(eq("BTC-USD"), eq(Timeframe.D1), any(), any()))
        .thenReturn(new CandleSeries("BTC-USD", "1d", null, List.of()));
    mockMvc
        .perform(get("/candles").param("pair", "BTC-USD").param("timeframe", "1y"))
        .andExpect(status().isOk());
    verify(candleService).series(eq("BTC-USD"), eq(Timeframe.D1), any(), any());
  }

  @Test
  void unknownPairReturns404() throws Exception {
    mockMvc
        .perform(get("/candles").param("pair", "NOPE-USD").param("timeframe", "1m"))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownTimeframeReturns400() throws Exception {
    mockMvc
        .perform(get("/candles").param("pair", "BTC-USD").param("timeframe", "3s"))
        .andExpect(status().isBadRequest());
  }
}
