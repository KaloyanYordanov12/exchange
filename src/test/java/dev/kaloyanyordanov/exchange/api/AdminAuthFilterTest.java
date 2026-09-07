package dev.kaloyanyordanov.exchange.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthFilterTest {

  private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
  private static final String HASH = ENCODER.encode("admin-key");

  private static void mockWriter(HttpServletResponse response) throws Exception {
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
  }

  @Test
  void validAdminKeyPassesThrough() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(AdminAuthFilter.ADMIN_KEY_HEADER)).thenReturn("admin-key");

    new AdminAuthFilter(HASH, ENCODER).doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void invalidKeyIsRejected() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(AdminAuthFilter.ADMIN_KEY_HEADER)).thenReturn("wrong");
    mockWriter(response);

    new AdminAuthFilter(HASH, ENCODER).doFilterInternal(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void missingKeyIsRejected() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(AdminAuthFilter.ADMIN_KEY_HEADER)).thenReturn(null);
    mockWriter(response);

    new AdminAuthFilter(HASH, ENCODER).doFilterInternal(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void blankConfiguredHashDisablesAdmin() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(AdminAuthFilter.ADMIN_KEY_HEADER)).thenReturn("admin-key");
    mockWriter(response);

    new AdminAuthFilter("", ENCODER).doFilterInternal(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(chain, never()).doFilter(any(), any());
  }
}
