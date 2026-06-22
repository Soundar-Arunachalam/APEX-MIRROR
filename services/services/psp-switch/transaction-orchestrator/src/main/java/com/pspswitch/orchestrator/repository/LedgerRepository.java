package com.pspswitch.orchestrator.repository;

import com.pspswitch.orchestrator.model.LedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for LedgerEntity.
 * Provides CRUD operations for the ledger_entries table.
 */
@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntity, String> {
}
