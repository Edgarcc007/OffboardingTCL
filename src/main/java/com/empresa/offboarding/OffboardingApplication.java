package com.empresa.offboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OffboardingApplication {

    private OffboardingApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(OffboardingApplication.class, args);
    }
}