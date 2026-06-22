package com.bankingswitch.cbsadapter.service;

import com.bankingswitch.cbsadapter.model.CbsApiResponse;
import com.bankingswitch.cbsadapter.model.CbsOperationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbsClientService {

    private final RestTemplate restTemplate;

    @Value("${cbs.host}")
    private String cbsHost;

    public CbsApiResponse getBalance(String vpa) {
        String url = cbsHost + "/cbs/balance/" + vpa;
        log.info("Calling CBS balance endpoint: {}", url);
        try {
            return restTemplate.getForObject(url, CbsApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to get balance from CBS", e);
            return CbsApiResponse.builder().status("FAILED").errorCode("CBS_UNAVAILABLE").build();
        }
    }

    public CbsApiResponse processDebit(String txnId, String vpa, Double amount) {
        String url = cbsHost + "/cbs/debit";
        CbsOperationRequest req = CbsOperationRequest.builder().txnId(txnId).vpa(vpa).amount(amount).build();
        log.info("Calling CBS debit endpoint: {}", url);
        try {
            return restTemplate.postForObject(url, req, CbsApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to process debit in CBS", e);
            return CbsApiResponse.builder().status("FAILED").errorCode("CBS_UNAVAILABLE").build();
        }
    }

    public CbsApiResponse processCredit(String txnId, String vpa, Double amount) {
        String url = cbsHost + "/cbs/credit";
        CbsOperationRequest req = CbsOperationRequest.builder().txnId(txnId).vpa(vpa).amount(amount).build();
        log.info("Calling CBS credit endpoint: {}", url);
        try {
            return restTemplate.postForObject(url, req, CbsApiResponse.class);
        } catch (Exception e) {
            log.error("Failed to process credit in CBS", e);
            return CbsApiResponse.builder().status("FAILED").errorCode("CBS_UNAVAILABLE").build();
        }
    }
}
