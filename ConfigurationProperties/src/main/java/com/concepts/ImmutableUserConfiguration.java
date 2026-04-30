package com.concepts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "user")
@Validated
public class ImmutableUserConfiguration {

    private final String name;
    @Min(1)
    private final int age;
    private final boolean active;

    ImmutableUserConfiguration(String name, int age, boolean active) {

        this.name = name;
        this.age = age;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public boolean isActive() {
        return active;
    }
}
