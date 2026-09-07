package dev.kaloyanyordanov.exchange.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request by its {@code X-API-Key} header, resolving it to an
 * account id via the {@link TraderRegistry} and attributing the request to that
 * account. A missing or invalid key yields {@code 401} and the request never
 * reaches a controller.
 */
public final class ApiKeyAuthFilter extends OncePerRequestFilter {

  /** Request header carrying the trader's API key. */
  public static final String API_KEY_HEADER = "X-API-Key";
  /** Request attribute holding the resolved account id (a {@code Long}). */
  public static final String ACCOUNT_ATTRIBUTE = "exchange.accountId";

  private final TraderRegistry registry;

  /**
   * Creates the filter.
   *
   * @param registry the trader registry used to resolve API keys
   */
  public ApiKeyAuthFilter(TraderRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<Long> accountId = registry.authenticate(request.getHeader(API_KEY_HEADER));
    if (accountId.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
      response.getWriter().write("{\"error\":\"missing or invalid API key\"}");
      return;
    }
    request.setAttribute(ACCOUNT_ATTRIBUTE, accountId.get());
    chain.doFilter(request, response);
  }
}
