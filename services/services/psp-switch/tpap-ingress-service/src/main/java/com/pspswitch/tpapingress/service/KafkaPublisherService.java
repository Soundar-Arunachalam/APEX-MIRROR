package com.pspswitch.tpapingress.service;

import com.pspswitch.tpapingress.dto.request.BalanceInquiryRequest;
import com.pspswitch.tpapingress.dto.request.PaymentInitiateRequest;
import com.pspswitch.tpapingress.dto.request.VpaLookupRequest;
import com.pspswitch.tpapingress.kafka.KafkaEventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes validated events to the correct Kafka topic.
 * Partition key = txnId (ensures ordering per transaction).
 * See architecture_spec.md Section 3.
 */
@Slf4j
@Service
public class KafkaPublisherService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.vpa-lookup}")
    private String vpaLookupTopic;

    @Value("${app.kafka.topics.balance-inquiry}")
    private String balanceInquiryTopic;

    @Value("${app.kafka.topics.payment-initiate}")
    private String paymentInitiateTopic;

    public KafkaPublisherService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public boolean publishVpaLookup(VpaLookupRequest request) {
        return publish(vpaLookupTopic, "VPA_LOOKUP_REQUESTED", request.getTxnId(), request);
    }

    public boolean publishBalanceInquiry(BalanceInquiryRequest request) {
        return publish(balanceInquiryTopic, "BALANCE_INQUIRY_REQUESTED", request.getTxnId(), request);
    }

    public boolean publishPaymentInitiate(PaymentInitiateRequest request) {
        return publish(paymentInitiateTopic, "PAYMENT_INITIATE_REQUESTED", request.getTxnId(), request);
    }

    private boolean publish(String topic, String eventType, String txnId, Object data) {
        KafkaEventEnvelope envelope = KafkaEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .tpapId(extractTpapId(txnId))
                .txnId(txnId)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .schemaVersion("1.0")
                .data(data)
                .build();

        kafkaTemplate.send(topic, txnId, envelope);
        log.info("Published {} to topic {} for txnId={}", eventType, topic, txnId);
        return true;
    }

    private String extractTpapId(String txnId) {
        if (txnId != null && txnId.contains("-")) {
            return txnId.substring(0, txnId.indexOf('-'));
        }
        return "unknown";
    }
}
