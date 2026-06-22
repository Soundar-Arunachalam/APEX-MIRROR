package com.hpe.upi.psp.config;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import java.util.HashMap;
import java.util.Map;
@Configuration
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}") private String bootstrapServers;
    @Bean public ProducerFactory<String,String> producerFactory() {
        Map<String,Object> c = new HashMap<>();
        c.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        c.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        c.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        c.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(c);
    }
    @Bean public KafkaTemplate<String,String> kafkaTemplate() { return new KafkaTemplate<>(producerFactory()); }
}
