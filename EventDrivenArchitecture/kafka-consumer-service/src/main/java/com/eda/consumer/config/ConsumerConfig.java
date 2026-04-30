package com.eda.consumer.config;

import com.eda.consumer.model.Order;
import com.eda.consumer.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class ConsumerConfig {

    //@Bean
    public ConsumerFactory<String, Order> orderConsumerFactory(
            KafkaProperties props) {

        Map<String, Object> config = props.buildConsumerProperties();

        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Order.class.getName());

        return new DefaultKafkaConsumerFactory<>(config);
    }

    // @Bean
    public ConsumerFactory<String, Payment> paymentConsumerFactory(KafkaProperties props) {

        Map<String, Object> config = props.buildConsumerProperties();

        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Payment.class.getName());
        return new DefaultKafkaConsumerFactory<>(config);
    }

    // @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> orderKafkaListenerFactory(
            ConsumerFactory<String, Order> orderConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Order> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(orderConsumerFactory);
        return factory;
    }

    //@Bean
    public ConcurrentKafkaListenerContainerFactory<String, Payment> paymentKafkaListenerFactory(
            ConsumerFactory<String, Payment> paymentConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Payment> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(paymentConsumerFactory);
        return factory;
    }


    @Bean
    public NewTopic orderDltEventsTopic() {
        return TopicBuilder.name("order-events-dlt").build();
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template, ObjectMapper objectMapper) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template) {

            @Override
            protected ProducerRecord<Object, Object> createProducerRecord(
                    ConsumerRecord<?, ?> record, TopicPartition tp, Headers headers,
                    byte[] key, byte[] value) {

                Object finalKey = (key != null) ? key : covertToBytes(record.key(), objectMapper);

                Object finalValue = (value != null) ? value : covertToBytes(record.value(), objectMapper);

                return new ProducerRecord<>(tp.topic(), tp.partition(), finalKey, finalValue, headers);
            }
        };

        return recoverer;
    }

    private byte[] covertToBytes(Object obj, ObjectMapper objectMapper) {
        if (obj == null) return null;
        if (obj instanceof byte[]) return (byte[]) obj;

        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new RuntimeException("DLT serialization failed", e);
        }
    }


    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(1000L, 2));
        return handler;
    }
}

