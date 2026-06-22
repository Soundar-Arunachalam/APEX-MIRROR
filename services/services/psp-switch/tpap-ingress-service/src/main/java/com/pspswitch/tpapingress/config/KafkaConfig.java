package com.pspswitch.tpapingress.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.vpa-lookup}")
    private String vpaLookupTopic;

    @Value("${app.kafka.topics.balance-inquiry}")
    private String balanceInquiryTopic;

    @Value("${app.kafka.topics.payment-initiate}")
    private String paymentInitiateTopic;

    @Value("${app.kafka.topics.audit}")
    private String auditTopic;

    @Bean
    public NewTopic vpaLookupTopic() {
        return TopicBuilder.name(vpaLookupTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic balanceInquiryTopic() {
        return TopicBuilder.name(balanceInquiryTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentInitiateTopic() {
        return TopicBuilder.name(paymentInitiateTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic auditTopic() {
        return TopicBuilder.name(auditTopic).partitions(1).replicas(1).build();
    }
}
