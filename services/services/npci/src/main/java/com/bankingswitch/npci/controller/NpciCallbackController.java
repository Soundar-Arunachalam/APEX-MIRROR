package com.bankingswitch.npci.controller;

import com.bankingswitch.npci.service.CallbackCorrelationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/upi/callback")
public class NpciCallbackController {

    private final CallbackCorrelationService callbackCorrelationService;

    public NpciCallbackController(CallbackCorrelationService callbackCorrelationService) {
        this.callbackCorrelationService = callbackCorrelationService;
    }

    @PostMapping(value = "/RespBalEnq/{txnId}", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String respBalEnq(@PathVariable String txnId, @RequestBody String rawXml) {
        return callbackCorrelationService.processCallback(txnId, rawXml);
    }

    @PostMapping(value = "/RespPay/{txnId}", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String respPay(@PathVariable String txnId, @RequestBody String rawXml) {
        return callbackCorrelationService.processCallback(txnId, rawXml);
    }
}
