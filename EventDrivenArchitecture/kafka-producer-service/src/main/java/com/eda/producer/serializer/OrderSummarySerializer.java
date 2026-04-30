package com.eda.producer.serializer;

import com.eda.producer.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

import java.util.LinkedHashMap;
import java.util.Map;

public class OrderSummarySerializer implements Serializer<Order> {

    private final ObjectMapper objectMapper;

    public OrderSummarySerializer() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public byte[] serialize(String topic, Order order) {
        if (order == null) {
            return null;
        }

        try {
            // Build a map with only the fields we want to expose
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("orderId", order.getOrderId());
            summary.put("productId", order.getProductId());
            // only need these 2 ids in the event, rest removed
            return objectMapper.writeValueAsBytes(summary);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Order summary", e);
        }
    }
}

