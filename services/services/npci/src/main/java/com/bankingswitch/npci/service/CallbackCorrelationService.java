package com.bankingswitch.npci.service;

import com.bankingswitch.npci.model.dto.UpiResponse;
import com.bankingswitch.npci.model.entity.NpciTransactionLog;
import com.bankingswitch.npci.repository.TransactionLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class CallbackCorrelationService {

    private final RestTemplate restTemplate;
    private final TransactionLogRepository transactionLogRepository;
    private final NpciXmlService npciXmlService;

    @Value("${app.psp-callback-url}")
    private String pspCallbackUrl;

    public CallbackCorrelationService(RestTemplate restTemplate,
                                      TransactionLogRepository transactionLogRepository,
                                      NpciXmlService npciXmlService) {
        this.restTemplate = restTemplate;
        this.transactionLogRepository = transactionLogRepository;
        this.npciXmlService = npciXmlService;
    }

    public String processCallback(String txnId, String rawXml) {
        UpiResponse response = npciXmlService.parseResponse(rawXml);

        Optional<NpciTransactionLog> logOpt = transactionLogRepository.findById(txnId);
        if (logOpt.isPresent()) {
            NpciTransactionLog log = logOpt.get();
            log.setResponseXml(rawXml);
            if (response.getResp() != null) {
                log.setStatus(response.getResp().getResult());
            } else {
                log.setStatus("COMPLETED");
            }
            transactionLogRepository.save(log);
        }

        // Forward to PSP
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> entity = new HttpEntity<>(rawXml, headers);
            
            // The callback path logic could be generic based on type or just hit a single endpoint on PSP.
            // Based on plan, callback goes to /api/npci/callback or similar on NPCI Adapter.
            String type = response.getTxn() != null ? response.getTxn().getType() : "UNKNOWN";
            String path;
            if ("PAY".equalsIgnoreCase(type)) {
                path = "/npci/callback/resp-pay/" + txnId;
            } else if ("BALANCEENQUIRY".equalsIgnoreCase(type) || "BALANCE".equalsIgnoreCase(type)) {
                path = "/npci/callback/resp-bal-enq/" + txnId;
            } else if ("COLLECT".equalsIgnoreCase(type)) {
                path = "/npci/callback/inbound-collect/" + txnId;
            } else {
                path = "/upi/callback/" + type + "/" + txnId;
            }
            String targetUrl = pspCallbackUrl + path;
            restTemplate.postForEntity(targetUrl, entity, String.class);
        } catch (Exception e) {
            System.err.println("Failed to forward callback to PSP: " + e.getMessage());
        }

        return npciXmlService.buildAck(txnId, "Ack");
    }
}
