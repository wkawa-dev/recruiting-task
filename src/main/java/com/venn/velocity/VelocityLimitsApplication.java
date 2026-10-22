package com.venn.velocity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VelocityLimitsApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(VelocityLimitsApplication.class, args)));
    }
}
