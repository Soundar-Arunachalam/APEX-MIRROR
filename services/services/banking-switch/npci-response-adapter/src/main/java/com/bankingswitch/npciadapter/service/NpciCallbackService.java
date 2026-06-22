package com.bankingswitch.npciadapter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpciCallbackService {

    private final RestTemplate restTemplate;

    @Value("${npci.callback-url}")
    private String npciCallbackUrl;

    public void sendCallback(String txnType, String txnId, String xmlResponse) {
        String url = String.format("%s/%s/%s", npciCallbackUrl, txnType, txnId);
        log.info("Sending callback to NPCI: {}", url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        
        HttpEntity<String> request = new HttpEntity<>(xmlResponse, headers);
        
        try {
            restTemplate.postForObject(url, request, String.class);
            log.info("Callback sent successfully for txnId: {}", txnId);
        } catch (Exception e) {
            log.error("Failed to send callback to NPCI for txnId: " + txnId, e);
        }
    }
}
