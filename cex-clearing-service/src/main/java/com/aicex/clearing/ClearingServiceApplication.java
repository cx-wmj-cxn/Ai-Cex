package com.aicex.clearing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aicex")
public class ClearingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClearingServiceApplication.class, args);
    }
}
