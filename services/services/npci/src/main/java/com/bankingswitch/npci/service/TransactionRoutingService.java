package com.bankingswitch.npci.service;

import com.bankingswitch.npci.model.dto.UpiRequest;
import com.bankingswitch.npci.model.entity.BankEndpoint;
import com.bankingswitch.npci.model.entity.NpciTransactionLog;
import com.bankingswitch.npci.model.entity.VpaRegistryEntry;
import com.bankingswitch.npci.repository.BankEndpointRepository;
import com.bankingswitch.npci.repository.TransactionLogRepository;
import com.bankingswitch.npci.repository.VpaRegistryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class TransactionRoutingService {

    private final RestTemplate restTemplate;
    private final VpaRegistryRepository vpaRegistryRepository;
    private final BankEndpointRepository bankEndpointRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final NpciXmlService npciXmlService;

    @Value("${app.bankswitch-host}")
    private String defaultBankswitchHost;

    public TransactionRoutingService(RestTemplate restTemplate,
                                     VpaRegistryRepository vpaRegistryRepository,
                                     BankEndpointRepository bankEndpointRepository,
                                     TransactionLogRepository transactionLogRepository,
                                     NpciXmlService npciXmlService) {
        this.restTemplate = restTemplate;
        this.vpaRegistryRepository = vpaRegistryRepository;
        this.bankEndpointRepository = bankEndpointRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.npciXmlService = npciXmlService;
    }

    public String routeRequest(String rawXml, UpiRequest request) {
        // Log transaction
        NpciTransactionLog log = new NpciTransactionLog();
        log.setTxnId(request.getTxn().getId());
        log.setTxnType(request.getTxn().getType());
        
        String targetVpa = null;
        if ("ReqBalEnq".equals(request.getTxn().getType())) {
            targetVpa = request.getPayer().getVpa();
            log.setPayerVpa(targetVpa);
        } else if ("ReqPay".equals(request.getTxn().getType())) {
            targetVpa = request.getPayer().getVpa();
            log.setPayerVpa(request.getPayer().getVpa());
            log.setPayeeVpa(request.getPayee().getVpa());
            log.setAmount(request.getPayer().getAmount());
        }

        log.setStatus("INITIATED");
        log.setRequestXml(rawXml);
        transactionLogRepository.save(log);

        if (targetVpa == null) {
            return npciXmlService.buildAck(request.getTxn().getId(), "Resp" + request.getTxn().getType().substring(3));
        }

        Optional<VpaRegistryEntry> vpaEntry = vpaRegistryRepository.findById(targetVpa);
        String switchUrl = defaultBankswitchHost;
        
        if (vpaEntry.isPresent()) {
            Optional<BankEndpoint> endpoint = bankEndpointRepository.findById(vpaEntry.get().getBankCode());
            if (endpoint.isPresent()) {
                switchUrl = endpoint.get().getSwitchBaseUrl();
            }
        }

        String targetUrl = switchUrl + "/bank/upi/" + request.getTxn().getType();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(rawXml, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.setStatus("FAILED");
            transactionLogRepository.save(log);
            return npciXmlService.buildAck(request.getTxn().getId(), "Resp" + request.getTxn().getType().substring(3));
        }
    }
}
