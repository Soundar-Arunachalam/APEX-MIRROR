package com.bankingswitch.npciadapter.consumer;

import com.bankingswitch.npciadapter.model.NpciCallbackEvent;
import com.bankingswitch.npciadapter.service.NpciCallbackService;
import com.bankingswitch.npciadapter.service.XmlResponseBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpciResponseConsumer {

    private final XmlResponseBuilderService xmlBuilder;
    private final NpciCallbackService callbackService;

    @KafkaListener(topics = "${kafka.topic.npci-response}", groupId = "npci-response-adapter-group")
    public void consume(NpciCallbackEvent event) {
        log.info("Consumed NpciCallbackEvent: {}", event.getTxnId());
        String responseXml = xmlBuilder.buildResponseXml(event);
        callbackService.sendCallback(event.getTxnType(), event.getTxnId(), responseXml);
    }
}
