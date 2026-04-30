package com.eda.producer.controller;

import com.eda.producer.model.Order;
import com.eda.producer.model.Payment;
import com.eda.producer.service.OrderProducerService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    OrderProducerService orderProducerService;


    @PostMapping("/with-key")
    public ResponseEntity<String> sendWithKey(@RequestBody Order order) {
        orderProducerService.sendWithKey(order);
        return ResponseEntity.accepted().body("Order created and event published!");
    }

    @PostMapping("/with-key-payment")
    public ResponseEntity<String> sendWithKeyPayment(@RequestBody Payment payment) {
        orderProducerService.sendWithKeyPayment(payment);
        return ResponseEntity.accepted().body("Payment created and event published!");
    }

    @PostMapping("/with-no-key")
    public ResponseEntity<String> sendWithNoKey(@RequestBody Order order) {
        orderProducerService.sendWithNoKeyAndCustomSerializer(order);
        return ResponseEntity.accepted().body("Order created and event published!");
    }

    /**
     * IDEMPOTENCY DEMO — Send N messages rapidly with same key
     * POST http://localhost:8081/api/orders/idempotency-demo?count=10
     *
     * All messages use the same key (ORDER-IDEMPOTENCY-TEST) → same partition.
     * With max.in.flight=5, multiple batches are in-flight simultaneously.
     * Idempotency ensures ordering is preserved even if retries happen.
     *
     * After calling this, verify with:
     *   bin/kafka-dump-log.sh --deep-iteration --print-data-log \
     *     --files /tmp/broker1-logs/order-events-0/00000000000000000000.log
     *
     * Look for: producerId, baseSequence — they should be strictly sequential.
     */
    @PostMapping("/idempotency-demo")
    public ResponseEntity<String> idempotencyDemo(
            @RequestParam(defaultValue = "10") int count) {

        String orderId = "ORDER-IDEMPOTENCY-TEST";
        orderProducerService.sendIdempotencyDemo(orderId, count);

        return ResponseEntity.accepted().body(
            "Sent " + count + " messages with key=" + orderId + ". " +
            "Check producer logs for partition/offset, then run kafka-dump-log.sh to verify ordering."
        );
    }
}