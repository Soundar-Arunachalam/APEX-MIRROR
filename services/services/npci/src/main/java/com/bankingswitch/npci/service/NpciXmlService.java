package com.bankingswitch.npci.service;

import com.bankingswitch.npci.model.dto.UpiRequest;
import com.bankingswitch.npci.model.dto.UpiResponse;
import com.bankingswitch.npci.xml.XmlRequestParser;
import com.bankingswitch.npci.xml.XmlResponseBuilder;
import org.springframework.stereotype.Service;

@Service
public class NpciXmlService {

    private final XmlRequestParser parser;
    private final XmlResponseBuilder builder;

    public NpciXmlService(XmlRequestParser parser, XmlResponseBuilder builder) {
        this.parser = parser;
        this.builder = builder;
    }

    public UpiRequest parseRequest(String xml) {
        return parser.parse(xml);
    }

    public String buildResponse(UpiResponse response) {
        return builder.buildResponseXml(response);
    }

    public UpiResponse parseResponse(String xml) {
        return builder.parseResponse(xml);
    }

    public String buildAck(String txnId, String type) {
        UpiResponse resp = new UpiResponse();
        UpiResponse.Txn txn = new UpiResponse.Txn();
        txn.setId(txnId);
        txn.setType(type);
        resp.setTxn(txn);

        UpiResponse.Resp r = new UpiResponse.Resp();
        r.setResult("SUCCESS");
        resp.setResp(r);

        return builder.buildResponseXml(resp);
    }
}
