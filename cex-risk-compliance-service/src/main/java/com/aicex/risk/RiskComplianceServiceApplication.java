package com.aicex.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aicex")
public class RiskComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskComplianceServiceApplication.class, args);
    }
}
