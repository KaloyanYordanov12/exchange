package dev.kaloyanyordanov.exchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment controls for the load simulator, under {@code exchange.sim}. These are
 * safety/product knobs around the simulator; they never touch the matching core.
 *
 * @param publicMaxTraders hard cap on how many traders any {@code /admin/simulator/start}
 *     request may launch. A request above the cap is clamped down to it, so a visitor
 *     cannot exhaust a constrained deployment's memory. Unset or non-positive means
 *     unlimited (the local default).
 */
@ConfigurationProperties(prefix = "exchange.sim")
public record SimProperties(Integer publicMaxTraders) {

  /** Applies the unlimited default when unset or non-positive. */
  public SimProperties {
    publicMaxTraders =
        (publicMaxTraders == null || publicMaxTraders <= 0) ? Integer.MAX_VALUE : publicMaxTraders;
  }
}
