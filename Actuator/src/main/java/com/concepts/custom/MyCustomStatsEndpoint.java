package com.concepts.custom;

import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "my-custom-stats")
public class MyCustomStatsEndpoint {

    @ReadOperation
    public String readAll() {
        return "Hello, Spring Boot!";
    }

    @ReadOperation
    public String read(@Selector String name, @Selector String message) {
        return "Hello: " + name + " msg for you is: " + message;
    }

    @WriteOperation
    public String refresh() {
        // simulate say cache refresh
        return "refreshed";
    }

    @DeleteOperation
    public String remove(@Selector String key) {
        return "reset done for key: " + key;
    }


}

