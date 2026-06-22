package com.bankingswitch.npci.controller;

import com.bankingswitch.npci.model.dto.UpiRequest;
import com.bankingswitch.npci.model.dto.UpiResponse;
import com.bankingswitch.npci.service.NpciXmlService;
import com.bankingswitch.npci.service.TransactionRoutingService;
import com.bankingswitch.npci.service.VpaDiscoveryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/upi")
public class NpciUpiController {

    private final NpciXmlService xmlService;
    private final VpaDiscoveryService vpaDiscoveryService;
    private final TransactionRoutingService transactionRoutingService;

    public NpciUpiController(NpciXmlService xmlService,
                             VpaDiscoveryService vpaDiscoveryService,
                             TransactionRoutingService transactionRoutingService) {
        this.xmlService = xmlService;
        this.vpaDiscoveryService = vpaDiscoveryService;
        this.transactionRoutingService = transactionRoutingService;
    }

    @PostMapping(value = "/ReqValAdd", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String reqValAdd(@RequestBody String rawXml) {
        UpiRequest request = xmlService.parseRequest(rawXml);
        UpiResponse response = vpaDiscoveryService.processValAdd(request);
        return xmlService.buildResponse(response);
    }

    @PostMapping(value = "/ReqBalEnq", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String reqBalEnq(@RequestBody String rawXml) {
        UpiRequest request = xmlService.parseRequest(rawXml);
        return transactionRoutingService.routeRequest(rawXml, request);
    }

    @PostMapping(value = "/ReqPay", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String reqPay(@RequestBody String rawXml) {
        UpiRequest request = xmlService.parseRequest(rawXml);
        return transactionRoutingService.routeRequest(rawXml, request);
    }
}
