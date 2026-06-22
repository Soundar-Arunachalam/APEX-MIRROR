package com.psp.npci.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NPCI Adapter Microservice entry point.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Consumes events from Kafka topic {@code npci.outbound.request} (sent by the Orchestrator)</li>
 *   <li>Calls Mock NPCI outbound over plain HTTP (REST + XML)</li>
 *   <li>Exposes REST webhook endpoints that Mock NPCI calls back on</li>
 *   <li>Publishes results to Kafka topic {@code npci.inbound.response}</li>
 *   <li>Stores intermediate state in Redis (TTL 300 s)</li>
 * </ul>
 *
 * <p>{@code @EnableAsync} activates Spring's async task executor so that webhook
 * controllers can return an HTTP Ack immediately and process the payload
 * asynchronously on a background thread.
 */
@SpringBootApplication
@EnableAsync
public class NpciAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NpciAdapterApplication.class, args);
    }
}
