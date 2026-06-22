package com.pspswitch.tpapingress.exception;

public class KafkaUnavailableException extends RuntimeException {
    public KafkaUnavailableException(String message) {
        super(message);
    }
}
