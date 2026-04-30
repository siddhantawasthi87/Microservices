package com.concepts.Controller;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@RestController
public class App1Controller {

    @Autowired
    private RestClient restClient;

    @Autowired
    Tracer tracer;

    @GetMapping("/api")
    public String invokeApp2() {
        String response = restClient.get().uri("http://localhost:8082/api/hello").retrieve().body(String.class);
        return "Response from App2: " + response;
    }

    @GetMapping("/api/test/span")
    public String manualSpanTest() {

        //creating a new span here:
        /* this will return me the current span ongoing */
        Span parentSpan = tracer.currentSpan();

        /* create a child span under same trace id */
        Span childSpan = tracer.nextSpan(parentSpan).name("manual-child-span");
        //starts the timer, but does not activate the span as current span (current span is still parent only)
        childSpan.start();

        Tracer.SpanInScope spanInScope = tracer.withSpan(childSpan);
        try {
            // business logic
            Thread.sleep(70);
        } catch (Exception ex) {
            //handle exception here
        } finally {
            if (spanInScope != null) {
                spanInScope.close();    // restore parent span as current
            }
            childSpan.end(); //stops the timer
        }

        return "Response from App1";
    }
}
