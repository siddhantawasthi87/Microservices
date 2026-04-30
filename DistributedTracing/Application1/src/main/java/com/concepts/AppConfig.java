package com.concepts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    RestClient restClientInstance(RestClient.Builder builder) {
        return builder.build();
    }
}