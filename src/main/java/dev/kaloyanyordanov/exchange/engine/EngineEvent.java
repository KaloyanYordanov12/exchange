package dev.kaloyanyordanov.exchange.engine;

/**
 * An immutable event emitted by the matching thread to the outbound stream for
 * downstream consumers (persistence and broadcast, wired in later phases). Sealed
 * so consumers can dispatch exhaustively.
 */
public sealed interface EngineEvent
    permits OrderAccepted, OrderRejected, TradeExecuted, BookChanged, AccountUpdated,
        CashDeposited, CashWithdrawn {
}
