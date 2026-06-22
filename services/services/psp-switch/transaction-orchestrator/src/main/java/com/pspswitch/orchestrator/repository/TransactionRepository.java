package com.pspswitch.orchestrator.repository;

import com.pspswitch.orchestrator.model.TransactionEntity;
import com.pspswitch.orchestrator.model.TransactionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TransactionEntity.
 * Provides CRUD + custom query methods for PostgreSQL persistence.
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    /**
     * Find transaction by composite key (tr + pa) — used for lookup endpoint.
     */
    Optional<TransactionEntity> findByTrAndPa(String tr, String pa);

    /**
     * Count transactions by state — used for control/status dashboard.
     */
    long countByState(TransactionState state);

    /**
     * Find all transactions in a given state — useful for admin/debugging.
     */
    List<TransactionEntity> findByState(TransactionState state);
}
