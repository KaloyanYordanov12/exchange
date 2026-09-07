package dev.kaloyanyordanov.exchange.engine;

/**
 * An internal command asking the matching thread to publish a consistent
 * {@link EngineSnapshot}, stamped with {@code requestId} so the requester can
 * tell when its snapshot (or a later one) is ready. Submitted through the real
 * ingress like any command; it does not change the book or ledger.
 *
 * @param requestId a monotonic id identifying (at least) this request
 */
record SnapshotRequest(long requestId) implements Command {}
