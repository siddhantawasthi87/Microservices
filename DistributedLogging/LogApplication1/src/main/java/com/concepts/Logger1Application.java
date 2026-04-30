package com.concepts;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Logger1Application {

    public static void main(String[] args) {

        SpringApplication.run(Logger1Application.class, args);
    }
}