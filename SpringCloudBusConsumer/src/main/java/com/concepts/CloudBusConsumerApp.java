package com.concepts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.context.annotation.Bean;

import java.util.function.Consumer;

@SpringBootApplication
@RemoteApplicationEventScan (basePackages = "com.concepts")
public class CloudBusConsumerApp {
    public static void main(String[] args) {
        SpringApplication.run(CloudBusConsumerApp.class, args);
    }
}



