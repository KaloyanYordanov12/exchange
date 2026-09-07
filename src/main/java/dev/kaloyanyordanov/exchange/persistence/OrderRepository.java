package dev.kaloyanyordanov.exchange.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the append-only orders audit table. */
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {}
