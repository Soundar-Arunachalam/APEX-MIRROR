package com.bankingswitch.listener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Value("${kafka.topic.inbound-txn}")
    private String inboundTopic;

    @Bean
    public NewTopic inboundTxnTopic() {
        return TopicBuilder.name(inboundTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
