package com.hpe.upi.npci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NpciRouterApplication {
    public static void main(String[] args) {
        SpringApplication.run(NpciRouterApplication.class, args);
    }
}
