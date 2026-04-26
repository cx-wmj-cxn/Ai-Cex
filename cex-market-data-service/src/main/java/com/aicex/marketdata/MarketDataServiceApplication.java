package com.aicex.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aicex")
public class MarketDataServiceApplication {

    public static void main(String[] args) {
        // 统一扫描 com.aicex，便于公共组件复用与服务治理接入。
        SpringApplication.run(MarketDataServiceApplication.class, args);
    }
}
