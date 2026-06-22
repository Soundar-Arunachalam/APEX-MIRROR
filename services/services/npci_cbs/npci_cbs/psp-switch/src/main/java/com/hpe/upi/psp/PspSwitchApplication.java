package com.hpe.upi.psp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PspSwitchApplication {
    public static void main(String[] args) {
        SpringApplication.run(PspSwitchApplication.class, args);
    }
}
