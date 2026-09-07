package dev.kaloyanyordanov.exchange.api;

/**
 * Request body to register an account.
 *
 * @param name a caller-supplied identifier for the account
 */
public record RegistrationRequest(String name) {}
