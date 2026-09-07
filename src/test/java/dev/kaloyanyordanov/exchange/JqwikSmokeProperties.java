package dev.kaloyanyordanov.exchange;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/**
 * Proves jqwik runs inside {@code mvn verify} and its properties are counted.
 * Trivial on purpose: the correctness phases lean on jqwik heavily, so wiring
 * it now de-risks later work.
 */
class JqwikSmokeProperties {

  /**
   * For all non-negative longs {@code a, b}, {@code a + b >= a}. The addends are
   * capped at {@code Long.MAX_VALUE / 2} so the sum cannot overflow, which keeps
   * the property genuinely true rather than accidentally falsifiable.
   */
  @Property
  boolean sumIsAtLeastEachAddend(
      @ForAll @LongRange(min = 0L, max = Long.MAX_VALUE / 2) long a,
      @ForAll @LongRange(min = 0L, max = Long.MAX_VALUE / 2) long b) {
    return a + b >= a;
  }
}
