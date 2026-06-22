package com.bankingswitch.npci.xml;

import com.bankingswitch.npci.model.dto.UpiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

@Component
public class XmlResponseBuilder {

    private final XmlMapper xmlMapper;

    public XmlResponseBuilder() {
        this.xmlMapper = new XmlMapper();
    }

    public String buildResponseXml(UpiResponse response) {
        try {
            return xmlMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to generate XML", e);
        }
    }

    public UpiResponse parseResponse(String xml) {
        try {
            return xmlMapper.readValue(xml, UpiResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }
}
