package com.hpe.upi.dashboard.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.dashboard.model.TransactionEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
@Component
public class DashboardKafkaConsumer {
    private final SimpMessagingTemplate ws;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    public DashboardKafkaConsumer(SimpMessagingTemplate ws) { this.ws = ws; }
    @KafkaListener(topics = {"upi.dashboard.events","upi.transactions.status"}, groupId = "dashboard-service")
    public void onEvent(String message) {
        try {
            TransactionEvent event = mapper.readValue(message, TransactionEvent.class);
            ws.convertAndSend("/topic/transactions", event);
        } catch (Exception e) { System.err.println("[DASHBOARD] Error: " + e.getMessage()); }
    }
}
