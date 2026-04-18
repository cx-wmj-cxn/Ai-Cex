package com.aicex.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aicex")
public class OpsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsServiceApplication.class, args);
    }
}
