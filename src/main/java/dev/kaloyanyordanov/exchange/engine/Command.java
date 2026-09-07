package dev.kaloyanyordanov.exchange.engine;

/**
 * An inbound command offered to the ingress queue by any producer thread and
 * applied, one at a time, by the single matching thread. Sealed so the matching
 * thread can exhaustively dispatch on the concrete command types.
 */
public sealed interface Command permits SubmitOrder, SnapshotRequest, DepositCash, WithdrawCash {
}
