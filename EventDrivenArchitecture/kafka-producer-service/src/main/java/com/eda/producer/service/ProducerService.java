package com.eda.producer.service;

import com.eda.producer.model.Order;

import com.eda.producer.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrder(Order order) {
        String key = order.getOrderId();

        kafkaTemplate.send("order-events", key, order);
    }

    public void sendPayment(Payment payment) {
        String key = payment.getPaymentId();

        kafkaTemplate.send("payment-events", key, payment);
    }

}
