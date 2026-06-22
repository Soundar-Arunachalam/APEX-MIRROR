package com.pspswitch.tpapegress.repository;

import com.pspswitch.tpapegress.model.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing persistence operations on {@link DeliveryLog} entities.
 * Handles exact logging traces during each Webhook execution trial.
 *
 * @since 1.0
 */
@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
}
