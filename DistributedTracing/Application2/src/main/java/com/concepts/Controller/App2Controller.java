package com.concepts.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class App2Controller {

    @GetMapping("/api/hello")
    public String helloMethod() {
        return "hello from app2";
    }
}