package com.bankingswitch.npci.model.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
import java.math.BigDecimal;

@Data
@JacksonXmlRootElement(localName = "UpiRequest")
public class UpiRequest {
    
    @JacksonXmlProperty(localName = "Txn")
    private Txn txn;
    
    @JacksonXmlProperty(localName = "Payer")
    private Payer payer;
    
    @JacksonXmlProperty(localName = "Payee")
    private Payee payee;

    @Data
    public static class Txn {
        @JacksonXmlProperty(isAttribute = true)
        private String id;
        @JacksonXmlProperty(isAttribute = true)
        private String type;
    }

    @Data
    public static class Payer {
        @JacksonXmlProperty(isAttribute = true, localName = "addr")
        private String vpa;
        @JacksonXmlProperty(isAttribute = true)
        private BigDecimal amount;
    }

    @Data
    public static class Payee {
        @JacksonXmlProperty(isAttribute = true, localName = "addr")
        private String vpa;
    }
}
