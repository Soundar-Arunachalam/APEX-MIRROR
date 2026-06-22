package com.pspswitch.npciresponse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topic that this service produces to.
 * Topics are auto-created if not present (useful for local dev).
 */
@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.inbound-response}")
    private String inboundResponseTopic;

    @Bean
    public NewTopic inboundResponseTopic() {
        return TopicBuilder.name(inboundResponseTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
