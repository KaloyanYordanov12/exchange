package dev.kaloyanyordanov.exchange.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates the admin surface (e.g. the simulator controls) behind a single bcrypt
 * admin key presented as {@code X-Admin-Key}. A missing or invalid key — or an
 * unconfigured admin hash — yields {@code 401}. Mirrors the trader API-key gate.
 */
public final class AdminAuthFilter extends OncePerRequestFilter {

  /** Request header carrying the admin key. */
  public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

  private final String adminKeyHash;
  private final PasswordEncoder encoder;

  /**
   * Creates the filter.
   *
   * @param adminKeyHash the bcrypt hash of the admin key ({@code null}/blank disables admin)
   */
  public AdminAuthFilter(String adminKeyHash) {
    this(adminKeyHash, new BCryptPasswordEncoder());
  }

  /**
   * Creates the filter with an explicit encoder (for tests).
   *
   * @param adminKeyHash the bcrypt hash of the admin key
   * @param encoder      the password encoder
   */
  AdminAuthFilter(String adminKeyHash, PasswordEncoder encoder) {
    this.adminKeyHash = adminKeyHash;
    this.encoder = encoder;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getHeader(ADMIN_KEY_HEADER);
    if (adminKeyHash != null && !adminKeyHash.isBlank() && key != null
        && encoder.matches(key, adminKeyHash)) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "AdminKey");
    response.getWriter().write("{\"error\":\"missing or invalid admin key\"}");
  }
}
