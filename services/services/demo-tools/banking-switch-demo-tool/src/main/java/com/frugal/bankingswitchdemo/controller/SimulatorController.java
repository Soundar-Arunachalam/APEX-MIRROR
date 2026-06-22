package com.frugal.bankingswitchdemo.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulate")
public class SimulatorController {

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/reqBalEnq")
    public ResponseEntity<String> simulateReqBalEnq(@RequestBody Map<String, String> requestData) {
        String payerAddress = requestData.getOrDefault("payerAddress", "user@bank");
        String deviceId = requestData.getOrDefault("deviceId", "device123");
        String msgId = UUID.randomUUID().toString().replace("-", "");
        String txnId = UUID.randomUUID().toString().replace("-", "");

        String xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ReqBalEnq xmlns=\"http://npci.org/upi/schema/\" id=\"%s\">\n" +
                "    <Head ver=\"2.0\" ts=\"2024-05-18T10:00:00+05:30\" orgId=\"123456\" msgId=\"%s\"/>\n" +
                "    <Txn id=\"%s\" note=\"Balance Enquiry\" refId=\"1234\" refUrl=\"http://example.com\" ts=\"2024-05-18T10:00:00+05:30\" type=\"BalEnq\" />\n" +
                "    <Payer addr=\"%s\" name=\"John Doe\" seqNum=\"1\" type=\"PERSON\" code=\"1234\">\n" +
                "        <Device>\n" +
                "            <Tag name=\"MOBILE\" value=\"%s\"/>\n" +
                "        </Device>\n" +
                "    </Payer>\n" +
                "</ReqBalEnq>", msgId, msgId, txnId, payerAddress, deviceId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8080/bank/upi/ReqBalEnq", entity, String.class);
            return ResponseEntity.ok("Success: " + response.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/reqPay")
    public ResponseEntity<String> simulateReqPay(@RequestBody Map<String, String> requestData) {
        String payerAddress = requestData.getOrDefault("payerAddress", "user@bank");
        String payeeAddress = requestData.getOrDefault("payeeAddress", "merchant@bank");
        String amount = requestData.getOrDefault("amount", "100.00");
        String msgId = UUID.randomUUID().toString().replace("-", "");
        String txnId = UUID.randomUUID().toString().replace("-", "");

        String xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ReqPay xmlns=\"http://npci.org/upi/schema/\" id=\"%s\">\n" +
                "    <Head ver=\"2.0\" ts=\"2024-05-18T10:00:00+05:30\" orgId=\"123456\" msgId=\"%s\"/>\n" +
                "    <Txn id=\"%s\" note=\"Payment\" refId=\"1234\" refUrl=\"http://example.com\" ts=\"2024-05-18T10:00:00+05:30\" type=\"PAY\" />\n" +
                "    <Payer addr=\"%s\" name=\"John Doe\" seqNum=\"1\" type=\"PERSON\" code=\"1234\">\n" +
                "        <Amount value=\"%s\" curr=\"INR\"/>\n" +
                "    </Payer>\n" +
                "    <Payees>\n" +
                "        <Payee addr=\"%s\" name=\"Merchant\" seqNum=\"1\" type=\"MERCHANT\" code=\"5678\">\n" +
                "            <Amount value=\"%s\" curr=\"INR\"/>\n" +
                "        </Payee>\n" +
                "    </Payees>\n" +
                "</ReqPay>", msgId, msgId, txnId, payerAddress, amount, payeeAddress, amount);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:8080/bank/upi/ReqPay", entity, String.class);
            return ResponseEntity.ok("Success: " + response.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
