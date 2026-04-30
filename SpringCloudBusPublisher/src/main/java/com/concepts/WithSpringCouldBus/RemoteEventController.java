package com.concepts.WithSpringCouldBus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RemoteEventController {

    @Autowired
    MyRemoteEventPublisher publisher;

    @GetMapping("/publish")
    public String publish(@RequestParam String message) {
        publisher.publish(message);
        return "Event published: " + message;
    }
}


