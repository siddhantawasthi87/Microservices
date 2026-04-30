package com.concepts.WithSpringCouldBus;

import org.springframework.cloud.bus.event.RemoteApplicationEvent;

//a shared file between producer and consumer
public class MyRemoteCustomEvent extends RemoteApplicationEvent {
    public MyRemoteCustomEvent() {} // default constructor for deserialization

    public MyRemoteCustomEvent(Object source, String originService, String destination, String message) {
        super(source, originService, destination);
        this.message = message;
    }

    private String message;

    public String getMessage() {
        return message;
    }
}
