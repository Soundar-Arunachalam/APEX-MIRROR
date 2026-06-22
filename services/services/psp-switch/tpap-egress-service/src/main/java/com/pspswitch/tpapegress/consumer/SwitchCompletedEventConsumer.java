package com.pspswitch.tpapegress.consumer;

import com.pspswitch.tpapegress.dispatcher.WebhookDispatcherService;
import com.pspswitch.tpapegress.exception.UnknownEventTypeException;
import com.pspswitch.tpapegress.model.event.SwitchCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens on the completed-events topic
 * and delegates to the webhook dispatcher.
 */
@Component
@Slf4j
public class SwitchCompletedEventConsumer {

    private final WebhookDispatcherService dispatcher;

    public SwitchCompletedEventConsumer(WebhookDispatcherService dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = "${tpap.egress.kafka.topic}",
            groupId = "tpap-egress-service"
    )
    public void consume(SwitchCompletedEvent event) {
        log.info("Consumed event eventId={} eventType={} tpapId={}",
                event.getEventId(), event.getEventType(), event.getTpapId());
        try {
            dispatcher.dispatch(event);
        } catch (UnknownEventTypeException e) {
            // Log and skip — no DLQ in v1.
            // TPAP can poll transaction status via the ingress service status endpoint.
            log.error("Unknown event type received, skipping: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unhandled error processing event eventId={}: {}",
                    event.getEventId(), e.getMessage(), e);
            throw e; // let Kafka retry via consumer error handler
        }
    }
}
