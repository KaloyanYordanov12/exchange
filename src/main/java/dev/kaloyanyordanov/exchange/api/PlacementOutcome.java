package dev.kaloyanyordanov.exchange.api;

/**
 * The outcome of placing an order through the API gateway.
 *
 * @param status  the outcome status
 * @param orderId the assigned order id when accepted, otherwise {@code -1}
 * @param message a human-readable reason when not accepted, otherwise {@code null}
 */
public record PlacementOutcome(Status status, long orderId, String message) {

  /** Placement outcome status. */
  public enum Status {
    /** Enqueued for matching. */
    ACCEPTED,
    /** Rejected before enqueue for invalid input. */
    INVALID,
    /** Rejected because the buyer could not reserve the cash to fund the order. */
    REJECTED,
    /** Rejected because the ingress queue is full (back-pressure). */
    BUSY
  }

  /**
   * An accepted placement.
   *
   * @param orderId the assigned order id
   * @return the outcome
   */
  public static PlacementOutcome accepted(long orderId) {
    return new PlacementOutcome(Status.ACCEPTED, orderId, null);
  }

  /**
   * An invalid placement.
   *
   * @param message why the input was rejected
   * @return the outcome
   */
  public static PlacementOutcome invalid(String message) {
    return new PlacementOutcome(Status.INVALID, -1L, message);
  }

  /**
   * A rejected placement (insufficient buying power).
   *
   * @param message why the order was rejected
   * @return the outcome
   */
  public static PlacementOutcome rejected(String message) {
    return new PlacementOutcome(Status.REJECTED, -1L, message);
  }

  /**
   * A busy (back-pressured) placement.
   *
   * @return the outcome
   */
  public static PlacementOutcome busy() {
    return new PlacementOutcome(Status.BUSY, -1L, "system busy");
  }
}
