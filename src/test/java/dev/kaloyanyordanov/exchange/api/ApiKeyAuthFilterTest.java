package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ApiKeyAuthFilterTest {

  private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

  private static ApiKeyAuthFilter filter() {
    TraderRegistry registry =
        new TraderRegistry(
            List.of(new TraderProperties(5L, ENCODER.encode("valid-key"), 0L, 0L)), ENCODER);
    return new ApiKeyAuthFilter(registry);
  }

  @Test
  void validKeyAttributesAccountAndContinues() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("valid-key");

    filter().doFilterInternal(request, response, chain);

    verify(request).setAttribute(ApiKeyAuthFilter.ACCOUNT_ATTRIBUTE, 5L);
    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void missingKeyIsRejectedWith401AndDoesNotContinue() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn(null);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter().doFilterInternal(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
    verify(request, never()).setAttribute(any(), any());
  }

  @Test
  void invalidKeyIsRejectedWith401() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("wrong-key");
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter().doFilterInternal(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
  }
}
