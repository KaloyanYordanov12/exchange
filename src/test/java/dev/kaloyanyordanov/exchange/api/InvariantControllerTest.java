package dev.kaloyanyordanov.exchange.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.exchange.invariant.CheckReport;
import dev.kaloyanyordanov.exchange.invariant.Invariant;
import dev.kaloyanyordanov.exchange.invariant.InvariantReport;
import dev.kaloyanyordanov.exchange.invariant.InvariantResult;
import dev.kaloyanyordanov.exchange.platform.ExchangeRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InvariantControllerTest {

  private final ExchangeRegistry registry = mock(ExchangeRegistry.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(registry.hasPair("BTC-USD")).thenReturn(true);
    mockMvc = MockMvcBuilders.standaloneSetup(new InvariantController(registry)).build();
  }

  @Test
  void latestReturnsThePairsLatestVerdict() throws Exception {
    when(registry.invariants("BTC-USD"))
        .thenReturn(
            InvariantReport.of(
                CheckReport.of(List.of(InvariantResult.pass(Invariant.ASSET_CONSERVATION)))));
    mockMvc
        .perform(get("/invariants").param("pair", "BTC-USD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.allPassed").value(true))
        .andExpect(jsonPath("$.results[0].invariant").value("ASSET_CONSERVATION"));
  }

  @Test
  void checkForcesFreshCheck() throws Exception {
    when(registry.checkInvariants("BTC-USD"))
        .thenReturn(
            InvariantReport.of(
                CheckReport.of(
                    List.of(InvariantResult.fail(Invariant.BOOK_NOT_CROSSED, "crossed")))));
    mockMvc
        .perform(get("/invariants/check").param("pair", "BTC-USD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allPassed").value(false))
        .andExpect(jsonPath("$.results[0].passed").value(false));
    verify(registry).checkInvariants("BTC-USD");
  }

  @Test
  void unknownPairReturns404() throws Exception {
    mockMvc
        .perform(get("/invariants").param("pair", "NOPE-USD"))
        .andExpect(status().isNotFound());
  }
}
