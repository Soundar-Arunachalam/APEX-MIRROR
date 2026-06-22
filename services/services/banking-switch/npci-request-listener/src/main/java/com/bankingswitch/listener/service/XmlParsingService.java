package com.bankingswitch.listener.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class XmlParsingService {

    private final XmlMapper xmlMapper;

    public XmlParsingService() {
        this.xmlMapper = new XmlMapper();
    }

    public String extractTxnId(String xml) {
        try {
            JsonNode rootNode = xmlMapper.readTree(xml.getBytes());
            JsonNode txnNode = rootNode.get("Txn");
            if (txnNode != null && txnNode.has("id")) {
                return txnNode.get("id").asText();
            }
            return "UNKNOWN";
        } catch (Exception e) {
            log.error("Failed to parse Txn ID from XML", e);
            return "UNKNOWN";
        }
    }
}
