package com.bankingswitch.cbsadapter.consumer;

import com.bankingswitch.cbsadapter.model.CbsApiResponse;
import com.bankingswitch.cbsadapter.model.CbsRequestEvent;
import com.bankingswitch.cbsadapter.model.CbsResponseEvent;
import com.bankingswitch.cbsadapter.producer.CbsResponseProducer;
import com.bankingswitch.cbsadapter.service.CbsClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbsRequestConsumer {

    private final CbsClientService cbsClientService;
    private final CbsResponseProducer cbsResponseProducer;

    @KafkaListener(topics = "${kafka.topic.cbs-request}", groupId = "cbs-adapter-group")
    public void consume(CbsRequestEvent event) {
        log.info("Consumed CbsRequestEvent: {} for operation {}", event.getTxnId(), event.getOperation());
        
        CbsApiResponse response;
        if ("BALANCE".equals(event.getOperation())) {
            response = cbsClientService.getBalance(event.getVpa());
        } else if ("DEBIT".equals(event.getOperation())) {
            response = cbsClientService.processDebit(event.getTxnId(), event.getVpa(), event.getAmount());
        } else if ("CREDIT".equals(event.getOperation())) {
            response = cbsClientService.processCredit(event.getTxnId(), event.getVpa(), event.getAmount());
        } else {
            response = CbsApiResponse.builder().status("FAILED").errorCode("INVALID_OP").build();
        }

        CbsResponseEvent respEvent = CbsResponseEvent.builder()
                .txnId(event.getTxnId())
                .operation(event.getOperation())
                .status(response.getStatus())
                .errorCode(response.getErrorCode())
                .balance(response.getBalance())
                .xmlPayload(event.getXmlPayload()) // pass through
                .build();
                
        cbsResponseProducer.sendResponse(respEvent);
    }
}
