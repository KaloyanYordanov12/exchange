package dev.kaloyanyordanov.exchange.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationControllerTest {

  private final TraderRegistry registry = mock(TraderRegistry.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registry)).build();
  }

  @Test
  void registrationReturnsTheKeyOnce() throws Exception {
    when(registry.register("carol")).thenReturn(new RegistrationResult(1000L, "secret-key"));
    mockMvc
        .perform(
            post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"carol\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(1000))
        .andExpect(jsonPath("$.apiKey").value("secret-key"));
  }

  @Test
  void invalidNameIsBadRequest() throws Exception {
    when(registry.register(" ")).thenThrow(new IllegalArgumentException("name is required"));
    mockMvc
        .perform(
            post("/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest());
  }
}
