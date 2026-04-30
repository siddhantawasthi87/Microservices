package com.concepts.WithoutSpringCloudBus;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MyEventListener {

    @EventListener
    public void handleEvent(MyCustomEvent event) {
        System.out.println("Received event message: " + event.getMessage());
    }
}




