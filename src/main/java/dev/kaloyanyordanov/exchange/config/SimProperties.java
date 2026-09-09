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
 * @param ambientTraders   total simulated traders for the always-on ambient market,
 *     spread across the pairs and auto-started at boot as a continuous, lightweight,
 *     human-paced run so the exchange looks alive with no manual launch. {@code 0}
 *     (the local default) turns it off. Each pair's share is itself clamped to
 *     {@code publicMaxTraders}.
 * @param publicMinThinkTimeMs minimum per-trader think-time, in milliseconds, that any
 *     {@code /admin/simulator/start} request is clamped up to: a request asking for a
 *     lower (or zero, i.e. max-speed) think-time has both its think-time bounds raised
 *     to this floor, so a visitor cannot run traders at zero think-time and flood a
 *     constrained deployment. Combined with {@code publicMaxTraders} this bounds the
 *     total order rate. {@code 0} (the local default) means unrestricted.
 */
@ConfigurationProperties(prefix = "exchange.sim")
public record SimProperties(
    Integer publicMaxTraders, Integer ambientTraders, Long publicMinThinkTimeMs) {

  /** Applies the defaults: unlimited cap, ambient off, no think-time floor. */
  public SimProperties {
    if (publicMaxTraders == null || publicMaxTraders <= 0) {
      publicMaxTraders = Integer.MAX_VALUE;
    }
    if (ambientTraders == null || ambientTraders < 0) {
      ambientTraders = 0;
    }
    if (publicMinThinkTimeMs == null || publicMinThinkTimeMs < 0) {
      publicMinThinkTimeMs = 0L;
    }
  }
}
