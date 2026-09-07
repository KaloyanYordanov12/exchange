package dev.kaloyanyordanov.exchange.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the append-only trades audit table. */
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {}
