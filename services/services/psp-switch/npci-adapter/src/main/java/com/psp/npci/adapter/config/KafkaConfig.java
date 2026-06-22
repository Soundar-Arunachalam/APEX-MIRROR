package com.psp.npci.adapter.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations.
 *
 * <p>Spring's {@code KafkaAdmin} auto-creates topics that are declared as {@link NewTopic}
 * beans if they do not already exist on the broker. In production the topics are typically
 * pre-provisioned; these beans are mainly useful for local development.
 *
 * <p>Producer and consumer connection properties are fully driven by
 * {@code application.yml} — no programmatic overrides here so that environment
 * variables ({@code KAFKA_BOOTSTRAP_SERVERS}) are respected seamlessly.
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.outbound-request}")
    private String outboundRequestTopic;

    @Value("${kafka.topics.inbound-response}")
    private String inboundResponseTopic;

    /**
     * Topic consumed by the Adapter — published by the Orchestrator.
     * Partitions: 3 | Replicas: 1 (increase in production).
     */
    @Bean
    public NewTopic outboundRequestTopic() {
        return TopicBuilder.name(outboundRequestTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic produced by the Adapter — consumed by Orchestrator + Notification Service.
     * Partitions: 3 | Replicas: 1 (increase in production).
     */
    @Bean
    public NewTopic inboundResponseTopic() {
        return TopicBuilder.name(inboundResponseTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
