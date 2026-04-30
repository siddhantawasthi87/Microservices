package com.concepts;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "user")
@Validated
public class UserConfigurations {

    @NotBlank(message = "name must not be empty")
    private String name;
//    @Min(value = 1, message = "Age can not be 0")
    private int age;
    private boolean active;
    private AddressConfig address;
    private List<String> roles;
    private List<Course> course;
    Map<String, String> preferences;
    Map<String, AddressConfig> locations;


    public static class AddressConfig {
        private String city;
        private String country;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }

    public static class Course {

        String name;
        boolean enrolled;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnrolled() {
            return enrolled;
        }

        public void setEnrolled(boolean enrolled) {
            this.enrolled = enrolled;
        }
    }


    // getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public AddressConfig getAddress() {
        return address;
    }

    public void setAddress(AddressConfig addressConfig) {
        this.address = addressConfig;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<Course> getCourse() {
        return course;
    }

    public void setCourse(List<Course> course) {
        this.course = course;
    }

    public Map<String, String> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, String> preferences) {
        this.preferences = preferences;
    }

    public Map<String, AddressConfig> getLocations() {
        return locations;
    }

    public void setLocations(Map<String, AddressConfig> locations) {
        this.locations = locations;
    }
}
