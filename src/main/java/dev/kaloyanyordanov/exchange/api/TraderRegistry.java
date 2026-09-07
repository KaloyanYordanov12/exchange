package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Resolves a presented API key to an account id by matching it against the
 * configured bcrypt hashes. This is trader identity — enough to attribute an
 * order to an account — not full authentication.
 */
public final class TraderRegistry {

  private final List<TraderProperties> traders;
  private final PasswordEncoder encoder;

  /**
   * Creates a registry using the default bcrypt encoder.
   *
   * @param traders the configured traders
   */
  public TraderRegistry(List<TraderProperties> traders) {
    this(traders, new BCryptPasswordEncoder());
  }

  /**
   * Creates a registry with an explicit encoder (for tests).
   *
   * @param traders the configured traders
   * @param encoder the password encoder used to match API keys
   */
  TraderRegistry(List<TraderProperties> traders, PasswordEncoder encoder) {
    this.traders = List.copyOf(traders);
    this.encoder = encoder;
  }

  /**
   * Resolves an API key to its account id.
   *
   * @param apiKey the presented API key
   * @return the account id if the key matches a configured trader, else empty
   */
  public Optional<Long> authenticate(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    for (TraderProperties trader : traders) {
      if (encoder.matches(apiKey, trader.apiKeyHash())) {
        return Optional.of(trader.accountId());
      }
    }
    return Optional.empty();
  }

  /**
   * The configured traders.
   *
   * @return an immutable list of traders
   */
  public List<TraderProperties> traders() {
    return traders;
  }
}
