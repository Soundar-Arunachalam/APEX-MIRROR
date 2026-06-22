package com.frugal.bankingswitchdemo.service;

import com.frugal.bankingswitchdemo.controller.SseController;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaMonitorService {

    private final SseController sseController;

    public KafkaMonitorService(SseController sseController) {
        this.sseController = sseController;
    }

    @KafkaListener(topics = {
            "upi.bank.txn.inbound",
            "upi.bank.cbs.request",
            "upi.bank.cbs.response",
            "upi.bank.npci.response"
    }, groupId = "banking-switch-demo-group")
    public void listen(String message, org.springframework.messaging.MessageHeaders headers) {
        String topic = headers.get(org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC, String.class);
        sseController.broadcastEvent(topic, message);
    }
}
