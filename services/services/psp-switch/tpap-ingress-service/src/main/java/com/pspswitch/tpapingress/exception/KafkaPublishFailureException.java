package com.pspswitch.tpapingress.exception;

public class KafkaPublishFailureException extends RuntimeException {
    public KafkaPublishFailureException(String message) {
        super(message);
    }
}
