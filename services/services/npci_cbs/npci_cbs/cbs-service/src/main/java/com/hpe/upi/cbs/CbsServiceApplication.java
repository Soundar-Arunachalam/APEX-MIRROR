package com.hpe.upi.cbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CbsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CbsServiceApplication.class, args);
    }
}
