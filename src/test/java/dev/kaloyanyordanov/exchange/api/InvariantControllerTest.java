package dev.kaloyanyordanov.exchange.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.exchange.invariant.CheckReport;
import dev.kaloyanyordanov.exchange.invariant.Invariant;
import dev.kaloyanyordanov.exchange.invariant.InvariantMonitor;
import dev.kaloyanyordanov.exchange.invariant.InvariantReport;
import dev.kaloyanyordanov.exchange.invariant.InvariantResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InvariantControllerTest {

  private final InvariantMonitor monitor = mock(InvariantMonitor.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new InvariantController(monitor)).build();
  }

  @Test
  void latestReturnsTheMonitorsLatestVerdict() throws Exception {
    when(monitor.latest())
        .thenReturn(
            InvariantReport.of(
                CheckReport.of(List.of(InvariantResult.pass(Invariant.CASH_CONSERVATION)))));
    mockMvc
        .perform(get("/invariants"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.allPassed").value(true))
        .andExpect(jsonPath("$.results[0].invariant").value("CASH_CONSERVATION"));
  }

  @Test
  void checkForcesFreshCheck() throws Exception {
    when(monitor.checkNow())
        .thenReturn(
            InvariantReport.of(
                CheckReport.of(
                    List.of(InvariantResult.fail(Invariant.BOOK_NOT_CROSSED, "crossed")))));
    mockMvc
        .perform(get("/invariants/check"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allPassed").value(false))
        .andExpect(jsonPath("$.results[0].passed").value(false));
    verify(monitor).checkNow();
  }
}
