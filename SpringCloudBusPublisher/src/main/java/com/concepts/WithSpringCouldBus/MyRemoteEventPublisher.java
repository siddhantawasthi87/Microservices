package com.concepts.WithSpringCouldBus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;


@Service
public class MyRemoteEventPublisher {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Value("${spring.cloud.bus.id}")
    String myBusID;

    public void publish(String msg) {
        publisher.publishEvent(new MyRemoteCustomEvent(this, myBusID, "*", msg));
    }

}


