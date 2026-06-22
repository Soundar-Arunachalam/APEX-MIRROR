package com.pspswitch.orchestrator.adapter;

import com.pspswitch.orchestrator.kafka.SwitchCompletedEventProducer;
import com.pspswitch.orchestrator.model.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Notification Service — Step 10 of the orchestration saga.
 *
 * <p>Publishes a {@code SwitchCompletedEvent} to the Kafka topic
 * {@code psp.switch.completed.events}. The tpap-egress-service consumes this
 * event and dispatches the appropriate typed webhook to the registered TPAP
 * endpoint with retry/backoff.
 *
 * <p>This replaces the direct HTTP POST to the TPAP webhook URL. Using Kafka
 * decouples the orchestrator from TPAP availability and provides built-in
 * retry semantics via the egress service.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SwitchCompletedEventProducer producer;

    public NotificationService(SwitchCompletedEventProducer producer) {
        this.producer = producer;
    }

    /**
     * Publishes a payment result notification to tpap-egress via Kafka.
     *
     * @param ctx the completed transaction context
     */
    public void notify(TransactionContext ctx) {
        log.info("[NOTIFICATION] Publishing switch-completed event | tid={} | state={}",
                ctx.getTid(), ctx.getState());
        producer.publishPaymentResult(ctx);
    }

    /**
     * Convenience overload kept for backward compatibility with WebhookController.
     */
    public void notify(String tid, String pa, BigDecimal am, String state) {
        log.info("[NOTIFICATION] notify called | tid={} | state={} (legacy path — context not available)",
                tid, state);
        // Legacy path — minimal logging only; prefer notify(TransactionContext)
    }

    public void notifyFailure(String tid, String pa, String reason) {
        log.info("[NOTIFICATION] notifyFailure | tid={} | reason={} (Kafka event already published by consumer)",
                tid, reason);
    }

    public void notifyCompensation(String tid, String pa, BigDecimal am) {
        log.info("[NOTIFICATION] notifyCompensation | tid={} (Kafka event already published by consumer)", tid);
    }
}
