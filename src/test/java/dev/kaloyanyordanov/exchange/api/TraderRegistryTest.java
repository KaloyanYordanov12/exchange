package dev.kaloyanyordanov.exchange.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.exchange.config.ExchangeProperties.TraderProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class TraderRegistryTest {

  // Low-strength encoder keeps the bcrypt hashing in these tests fast.
  private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

  private static TraderRegistry registryWith(String aliceKey, String bobKey) {
    return new TraderRegistry(
        List.of(
            new TraderProperties(1L, ENCODER.encode(aliceKey), 1_000L, 0L),
            new TraderProperties(2L, ENCODER.encode(bobKey), 0L, 50L)),
        ENCODER);
  }

  @Test
  void resolvesKnownKeyToItsAccount() {
    TraderRegistry registry = registryWith("alice-key", "bob-key");
    assertThat(registry.authenticate("alice-key")).contains(1L);
    assertThat(registry.authenticate("bob-key")).contains(2L);
  }

  @Test
  void rejectsAnUnknownKey() {
    TraderRegistry registry = registryWith("alice-key", "bob-key");
    assertThat(registry.authenticate("nope")).isEmpty();
  }

  @Test
  void rejectsNullOrBlankKey() {
    TraderRegistry registry = registryWith("alice-key", "bob-key");
    assertThat(registry.authenticate(null)).isEmpty();
    assertThat(registry.authenticate("  ")).isEmpty();
  }

  @Test
  void exposesTradersImmutably() {
    TraderRegistry registry = new TraderRegistry(List.of());
    assertThat(registry.traders()).isEmpty();
    assertThat(registry.authenticate("anything")).isEmpty();
  }
}
