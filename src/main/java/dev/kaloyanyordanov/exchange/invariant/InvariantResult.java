package dev.kaloyanyordanov.exchange.invariant;

/**
 * The result of checking one invariant.
 *
 * @param invariant the invariant checked
 * @param passed    whether it held
 * @param detail    a human-readable description of the failure, or {@code ""} if it passed
 */
public record InvariantResult(Invariant invariant, boolean passed, String detail) {

  /**
   * A passing result.
   *
   * @param invariant the invariant
   * @return a passing result
   */
  public static InvariantResult pass(Invariant invariant) {
    return new InvariantResult(invariant, true, "");
  }

  /**
   * A failing result.
   *
   * @param invariant the invariant
   * @param detail    why it failed
   * @return a failing result
   */
  public static InvariantResult fail(Invariant invariant, String detail) {
    return new InvariantResult(invariant, false, detail);
  }
}
