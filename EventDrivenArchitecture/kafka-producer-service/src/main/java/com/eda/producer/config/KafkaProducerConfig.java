package com.eda.producer.config;

import com.eda.producer.model.Order;
import com.eda.producer.serializer.OrderSummarySerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(2)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .config(TopicConfig.SEGMENT_BYTES_CONFIG, "1073741824")
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(2)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
                .config(TopicConfig.SEGMENT_BYTES_CONFIG, "1073741824")
                .build();
    }


    @Bean
    public KafkaTemplate<String, Order> customSerializerKafkaTemplate(KafkaProperties kafkaProperties) {

        // Start with all producer properties from application.properties
        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildProducerProperties(null));

        // Override ONLY the value serializer — everything else stays the same
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, OrderSummarySerializer.class);

        DefaultKafkaProducerFactory<String, Order> factory =
                new DefaultKafkaProducerFactory<>(props);

        return new KafkaTemplate<>(factory);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(KafkaProperties kafkaProperties) {

        // Start with all producer properties from application.properties
        Map<String, Object> props =
                new HashMap<>(kafkaProperties.buildProducerProperties(null));

        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(props);

        return new KafkaTemplate<>(factory);
    }
}

