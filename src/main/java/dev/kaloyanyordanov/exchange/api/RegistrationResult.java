package dev.kaloyanyordanov.exchange.api;

/**
 * The result of registering an account: the account id and the plaintext API key,
 * returned exactly once (only its bcrypt hash is retained).
 *
 * @param accountId the new account id
 * @param apiKey    the generated API key (shown once)
 */
public record RegistrationResult(long accountId, String apiKey) {}
