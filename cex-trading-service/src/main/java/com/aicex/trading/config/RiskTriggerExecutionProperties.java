package com.aicex.trading.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trading.risk-trigger.execution")
public class RiskTriggerExecutionProperties {

    @Min(10)
    private long idempotencyWindowSeconds = 600;

    public long getIdempotencyWindowSeconds() {
        return idempotencyWindowSeconds;
    }

    public void setIdempotencyWindowSeconds(long idempotencyWindowSeconds) {
        this.idempotencyWindowSeconds = idempotencyWindowSeconds;
    }
}
