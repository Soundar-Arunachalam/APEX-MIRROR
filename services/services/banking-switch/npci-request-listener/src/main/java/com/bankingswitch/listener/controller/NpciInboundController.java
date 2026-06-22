package com.bankingswitch.listener.controller;

import com.bankingswitch.listener.model.InboundTransactionEvent;
import com.bankingswitch.listener.producer.TransactionEventProducer;
import com.bankingswitch.listener.service.RequestValidationService;
import com.bankingswitch.listener.service.XmlParsingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/bank/upi")
@RequiredArgsConstructor
@Slf4j
public class NpciInboundController {

    private final TransactionEventProducer producer;
    private final XmlParsingService xmlParsingService;
    private final RequestValidationService validationService;

    @PostMapping(value = "/ReqBalEnq", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleReqBalEnq(@RequestBody String xmlPayload) {
        return processRequest(xmlPayload, "ReqBalEnq");
    }

    @PostMapping(value = "/ReqPay", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleReqPay(@RequestBody String xmlPayload) {
        return processRequest(xmlPayload, "ReqPay");
    }

    private ResponseEntity<String> processRequest(String xmlPayload, String txnType) {
        if (!validationService.validate(xmlPayload)) {
            return ResponseEntity.badRequest().body("<Ack api=\"" + txnType + "\" err=\"INVALID_XML\"/>");
        }

        String txnId = xmlParsingService.extractTxnId(xmlPayload);
        
        InboundTransactionEvent event = InboundTransactionEvent.builder()
                .txnId(txnId)
                .txnType(txnType)
                .xmlPayload(xmlPayload)
                .timestamp(System.currentTimeMillis())
                .build();
                
        producer.sendEvent(event);
        
        String ackXml = String.format("<Ack api=\"%s\" reqMsgId=\"%s\" err=\"\" ts=\"%s\"/>", 
            txnType, txnId, java.time.Instant.now().toString());
            
        return ResponseEntity.ok(ackXml);
    }
}
