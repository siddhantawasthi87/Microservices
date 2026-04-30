package com.concepts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserConfigurations userConfigurations;

    @Autowired
    ImmutableUserConfiguration immutableUserConfiguration;

    @GetMapping("/")
    public void getUserConfig() {
        System.out.println("name: " + userConfigurations.getAge());
        System.out.println("Age: " + userConfigurations.getName());
        System.out.println("is active: " + userConfigurations.isActive());
        System.out.println("Address: " + userConfigurations.getAddress().getCity()+":"+
                userConfigurations.getAddress().getCountry());
        System.out.println("Roles: " + userConfigurations.getRoles().get(0) + ":"+userConfigurations.getRoles().get(1));
        System.out.println("Courses: " + userConfigurations.getCourse().get(0).getName() + ":"+userConfigurations.getCourse().get(1).getName());
        System.out.println("Preferences: " + userConfigurations.getPreferences().get("theme"));
        System.out.println("Location: " + userConfigurations.getLocations().get("home").getCity());

        //immutable
        System.out.println("immutable name: " + immutableUserConfiguration.getName());
        System.out.println("immutable Age: " + immutableUserConfiguration.getAge());
        System.out.println("immutable is active: " + immutableUserConfiguration.isActive());


    }
}


