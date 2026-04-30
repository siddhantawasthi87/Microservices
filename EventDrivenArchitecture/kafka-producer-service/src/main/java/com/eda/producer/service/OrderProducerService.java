package com.eda.producer.service;

import com.eda.producer.model.Order;

import com.eda.producer.model.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    @Qualifier("customSerializerKafkaTemplate")
    private KafkaTemplate<String, Order> customSerializerKafkaTemplate;


    public void sendWithKey(Order order) {
        String key = order.getOrderId();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send("order-events", key, order);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Order event sent successfully!");
                System.out.println("Partition: " +  result.getRecordMetadata().partition());
                System.out.println("Offset: " + result.getRecordMetadata().offset());
            } else {
                System.out.println("Failed to send order event: " +  ex.getMessage());
            }
        });

    }

    public void sendWithKeyPayment(Payment payment) {
        String key = payment.getPaymentId();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send("payment-events", key, payment);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("payment event sent successfully!");
                System.out.println("Partition: " +  result.getRecordMetadata().partition());
                System.out.println("Offset: " + result.getRecordMetadata().offset());
            } else {
                System.out.println("Failed to send payment event: " +  ex.getMessage());
            }
        });
    }

    public void sendWithNoKeyAndCustomSerializer(Order order) {

        // No key → partition chosen by sticky partitioner
        CompletableFuture<SendResult<String, Order>> future =
                customSerializerKafkaTemplate.send("order-events", order);

        //handle the callback
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Order event sent successfully!");
                System.out.println("Partition: " +  result.getRecordMetadata().partition());
                System.out.println("Offset: " + result.getRecordMetadata().offset());
            } else {
                System.out.println("Failed to send order event: " +  ex.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // IDEMPOTENCY DEMO — Proves ordering is preserved
    // ═══════════════════════════════════════════════════════════

    /**
     * Sends multiple messages rapidly with the SAME KEY to the SAME partition.
     * All messages go to the same partition (same key = same hash).
     *
     * With max.in.flight=5, multiple batches can be in-flight simultaneously.
     * If a batch fails and retries, WITHOUT idempotency the order could break.
     *
     * WITH idempotency (enable.idempotence=true):
     *   - Each batch gets a sequence number (0, 1, 2, 3, ...)
     *   - Broker rejects out-of-order sequences
     *   - Broker forces correct ordering even with retries
     *
     * After this method runs, use kafka-dump-log.sh to verify:
     *   - All messages have sequential sequence numbers
     *   - Messages are in correct order (STEP-1, STEP-2, ..., STEP-N)
     */
    public List<String> sendIdempotencyDemo(String orderId, int messageCount) {
        List<String> results = new ArrayList<>();
        String key = orderId; // Same key → same partition → ordering matters

        System.out.println("***************************************");
        System.out.println("IDEMPOTENCY DEMO — Sending " + messageCount + " messages");
        System.out.println("Key: " + key + " (all go to SAME partition)");
        System.out.println("enable.idempotence=true");
        System.out.println("max.in.flight.requests.per.connection=5");
        System.out.println("***************************************");

        for (int i = 1; i <= messageCount; i++) {
            Order order = new Order();
            order.setOrderId(orderId);
            order.setCustomerId("CUST-1");
            order.setProductId("PROD-1");
            order.setQuantity(i);
            order.setTotalAmount(100.0 * i);
            order.setStatus("STEP-" + i);  // STEP-1, STEP-2, ... STEP-N

            final int messageNum = i;

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send("order-events", key, order);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    String msg = String.format(
                            "Message %d (STEP-%d) → Partition: %d, Offset: %d",
                            messageNum, messageNum,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                    System.out.println(msg);
                    synchronized (results) {
                        results.add(msg);
                    }
                } else {
                    System.out.println("Message " + messageNum + " FAILED: " + ex.getMessage());
                }
            });
        }

        return results;

    }
}
