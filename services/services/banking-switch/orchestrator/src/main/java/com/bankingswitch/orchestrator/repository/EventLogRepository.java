package com.bankingswitch.orchestrator.repository;

import com.bankingswitch.orchestrator.model.entity.EventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventLogRepository extends JpaRepository<EventLogEntity, Long> {
}
