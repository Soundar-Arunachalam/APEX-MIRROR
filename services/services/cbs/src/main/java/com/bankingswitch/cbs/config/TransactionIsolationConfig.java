package com.bankingswitch.cbs.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionIsolationConfig {
    // Transaction isolation levels are defined directly on the Service methods
    // utilizing Spring's @Transactional(isolation = Isolation.SERIALIZABLE)
}
