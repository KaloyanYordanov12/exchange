package dev.kaloyanyordanov.exchange.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the append-only ledger-transactions (cash movement) audit table. */
public interface LedgerTransactionRepository
    extends JpaRepository<LedgerTransactionEntity, Long> {}
