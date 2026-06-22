package com.bankingswitch.npci.xml;

import com.bankingswitch.npci.model.dto.UpiRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

@Component
public class XmlRequestParser {

    private final XmlMapper xmlMapper;

    public XmlRequestParser() {
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public UpiRequest parse(String xml) {
        try {
            return xmlMapper.readValue(xml, UpiRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }
}
