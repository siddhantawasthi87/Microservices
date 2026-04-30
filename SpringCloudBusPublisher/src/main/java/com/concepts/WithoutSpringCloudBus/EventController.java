package com.concepts.WithoutSpringCloudBus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    @Autowired
    private MyEventPublisher publisher;

    @GetMapping("/withinApp/publish")
    public String publish(@RequestParam String message) {
        publisher.publish(message);
        return "Event published: " + message;
    }
}



