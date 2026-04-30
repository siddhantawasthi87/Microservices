package com.concepts.customappender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class DbAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {

        //Extract log data
        String message = event.getFormattedMessage();
        String loggerName = event.getLoggerName();
        long timestamp = event.getTimeStamp();

        // Custom destination logic
        // (Pretend this is a DB insert)
        System.out.println("Saving ERROR log to DB: " +
                        timestamp + " - " + loggerName + " - " + message);
    }
}


