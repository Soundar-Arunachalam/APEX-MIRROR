package com.pspswitch.tpapegress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the TPAP Egress Service.
 * Bootstraps the Spring context for webhook dispatcher components.
 *
 * @since 1.0
 */
@SpringBootApplication
public class TpapEgressApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TpapEgressApplication.class, args);
    }
}
