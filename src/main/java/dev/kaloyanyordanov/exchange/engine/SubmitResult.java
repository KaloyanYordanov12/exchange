package dev.kaloyanyordanov.exchange.engine;

/** The outcome of offering a command to the ingress queue. */
public enum SubmitResult {
  /** The command was enqueued and will be processed by the matching thread. */
  ENQUEUED,
  /** The queue was full: back-pressure. The caller should back off and retry. */
  REJECTED_BUSY,
  /** The engine is not accepting commands (not started, or stopping). */
  REJECTED_NOT_RUNNING
}
