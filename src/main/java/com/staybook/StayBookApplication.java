package com.staybook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StayBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayBookApplication.class, args);
    }
}
