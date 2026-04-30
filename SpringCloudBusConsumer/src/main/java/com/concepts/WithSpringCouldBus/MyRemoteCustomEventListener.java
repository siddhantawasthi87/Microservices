package com.concepts.WithSpringCouldBus;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MyRemoteCustomEventListener {

    @EventListener
    public void handle(MyRemoteCustomEvent event) {
        System.out.println("Received remote event in client-service: " + event.getMessage());
    }
}



