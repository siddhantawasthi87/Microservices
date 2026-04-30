package com.concepts.controller;

import com.concepts.OrderProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private int counter = 0;

    @Autowired
    OrderProperties orderProperties;

    @GetMapping("/fetch")
    public String getOrders() {
        counter++;
        return "fetched orders and message: " + orderProperties.getMessage() +
                " and counter value is: " + counter;
    }
}

