package com.concepts.WithoutSpringCloudBus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class MyEventPublisher {

    @Autowired
    private  ApplicationEventPublisher publisher;

    public void publish(String msg) {
        publisher.publishEvent(new MyCustomEvent(this, msg));
    }
}


