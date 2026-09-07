package dev.kaloyanyordanov.exchange.api;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Trader identity: resolves an API key to an account id, and registers new
 * accounts at runtime. Registration returns the plaintext key exactly once; only
 * its bcrypt hash is retained (never plaintext at rest). This is enough to
 * attribute an order to an account — not full authentication. Identity is held in
 * memory (registered accounts do not survive a restart); persisting identity is
 * out of scope for v1.
 */
public final class TraderRegistry {

  private static final SecureRandom RANDOM = new SecureRandom();
  // Registered account ids start well above the configured demo ids.
  private static final long REGISTERED_ID_BASE = 1_000L;

  private record Entry(long accountId, String apiKeyHash) {}

  private final List<Entry> entries = new CopyOnWriteArrayList<>();
  private final PasswordEncoder encoder;
  private final AtomicLong nextAccountId;

  /**
   * Creates a registry seeded with the configured traders, using the default
   * bcrypt encoder.
   *
   * @param configuredTraders the configured traders
   */
  public TraderRegistry(List<TraderProperties> configuredTraders) {
    this(configuredTraders, new BCryptPasswordEncoder());
  }

  /**
   * Creates a registry with an explicit encoder (for tests).
   *
   * @param configuredTraders the configured traders
   * @param encoder           the password encoder used to hash and match API keys
   */
  TraderRegistry(List<TraderProperties> configuredTraders, PasswordEncoder encoder) {
    this.encoder = encoder;
    long maxId = 0L;
    for (TraderProperties trader : configuredTraders) {
      entries.add(new Entry(trader.accountId(), trader.apiKeyHash()));
      maxId = Math.max(maxId, trader.accountId());
    }
    this.nextAccountId = new AtomicLong(Math.max(maxId + 1L, REGISTERED_ID_BASE));
  }

  /**
   * Resolves an API key to its account id.
   *
   * @param apiKey the presented API key
   * @return the account id if the key matches a known account, else empty
   */
  public Optional<Long> authenticate(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    for (Entry entry : entries) {
      if (encoder.matches(apiKey, entry.apiKeyHash())) {
        return Optional.of(entry.accountId());
      }
    }
    return Optional.empty();
  }

  /**
   * Registers a new account, returning its id and a freshly generated API key
   * (shown once). Only the bcrypt hash of the key is stored. New accounts start
   * with zero balances.
   *
   * @param name a caller-supplied identifier (required, not otherwise used in v1)
   * @return the registration result
   */
  public RegistrationResult register(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    long accountId = nextAccountId.getAndIncrement();
    String apiKey = generateApiKey();
    entries.add(new Entry(accountId, encoder.encode(apiKey)));
    return new RegistrationResult(accountId, apiKey);
  }

  /**
   * The number of known accounts (configured + registered).
   *
   * @return the account count
   */
  public int size() {
    return entries.size();
  }

  private static String generateApiKey() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
