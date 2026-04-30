package com.eda.consumer.listener;

import com.eda.consumer.model.Order;
import com.eda.consumer.model.Payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    @Autowired
    ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events")
    public void consumeOrder(Order order) {

        System.out.println("Received Order event:: " + order.getOrderId());

    }


   // @KafkaListener(topics = "payment-events", containerFactory = "paymentKafkaListenerFactory")
    public void consumePayment(Payment payment) {
        //business logic
        System.out.println("Received payment event:: " + payment.getPaymentId());
    }

    /*
        Manual mapping
     */
    // @KafkaListener(topics = {"order-events", "payment-events"})
    public void consumeManualMapping(ConsumerRecord<String, String> record) throws JsonProcessingException, JsonMappingException {
        if ("order-events".equals(record.topic())) {

            Order order = objectMapper.readValue(record.value(), Order.class);
            // business logic for order
            System.out.println("order: " + order.getOrderId());
        } else if ("payment-events".equals(record.topic())) {

            Payment payment = objectMapper.readValue(record.value(), Payment.class);
            System.out.println("payment: " + payment.getPaymentId());
        }
    }

    /*
       Auto mapping
    */
    // @KafkaListener(topics = {"order-events", "payment-events"})
    public void consumeAutoMapping(ConsumerRecord<String, Object> record) {
        if ("order-events".equals(record.topic())) {
            Order order = (Order) record.value();
            System.out.println("Order:: " + order.getOrderId());
        } else if ("payment-events".equals(record.topic())) {
            Payment payment = (Payment) record.value();
            System.out.println("Payment:: " + payment.getPaymentId());
        }
    }
}

