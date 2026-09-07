package dev.kaloyanyordanov.exchange;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Smoke test: the application boots and the actuator health endpoint reports UP.
 * No database, no Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointSmokeTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void healthEndpointReportsUp() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
