package com.concepts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {

    Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Async
    public void processPayment() {
        log.info("Async-Payment processed successfully");
    }
}


