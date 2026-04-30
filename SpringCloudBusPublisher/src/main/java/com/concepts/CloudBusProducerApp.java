package com.concepts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;

@SpringBootApplication
public class CloudBusProducerApp {
    public static void main(String[] args) {
        SpringApplication.run(CloudBusProducerApp.class, args);
    }
}



