package com.mumbai.evacuation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EvacuationApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvacuationApplication.class, args);
    }
}
