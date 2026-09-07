package dev.kaloyanyordanov.exchange.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void emptyRegistryHasNoAccounts() {
    TraderRegistry registry = new TraderRegistry(List.of(), ENCODER);
    assertThat(registry.size()).isZero();
    assertThat(registry.authenticate("anything")).isEmpty();
  }

  @Test
  void registrationReturnsWorkingKeyStoringHashOnly() {
    TraderRegistry registry = registryWith("alice-key", "bob-key");
    RegistrationResult result = registry.register("carol");

    // The account id is distinct from the configured ones.
    assertThat(result.accountId()).isGreaterThan(2L);
    assertThat(result.apiKey()).isNotBlank();
    // The returned key authenticates to the new account.
    assertThat(registry.authenticate(result.apiKey())).contains(result.accountId());
    assertThat(registry.size()).isEqualTo(3);
  }

  @Test
  void eachRegistrationGetsDistinctIdAndKey() {
    TraderRegistry registry = new TraderRegistry(List.of(), ENCODER);
    RegistrationResult first = registry.register("a");
    RegistrationResult second = registry.register("b");
    assertThat(first.accountId()).isNotEqualTo(second.accountId());
    assertThat(first.apiKey()).isNotEqualTo(second.apiKey());
  }

  @Test
  void registrationRequiresName() {
    TraderRegistry registry = new TraderRegistry(List.of(), ENCODER);
    assertThatThrownBy(() -> registry.register(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
