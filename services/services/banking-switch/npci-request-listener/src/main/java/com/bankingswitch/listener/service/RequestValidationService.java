package com.bankingswitch.listener.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RequestValidationService {

    public boolean validate(String xml) {
        if (xml == null || xml.isEmpty()) {
            return false;
        }
        // Basic validation: verify that the XML contains <Txn> tags
        return xml.contains("<Txn");
    }
}
