package dev.kaloyanyordanov.exchange.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for account balance snapshots. */
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {}
