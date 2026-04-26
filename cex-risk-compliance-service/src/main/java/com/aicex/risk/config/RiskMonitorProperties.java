package com.aicex.risk.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "risk.monitor")
public class RiskMonitorProperties {

    @Min(100)
    private long intervalMs = 1000L;

    @Min(1)
    private int liquidationConsecutiveHits = 2;

    @Min(1)
    private int conditionalConsecutiveHits = 1;

    @DecimalMin("0.0")
    private BigDecimal liquidationFeeBufferRate = new BigDecimal("0.001");

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getLiquidationConsecutiveHits() {
        return liquidationConsecutiveHits;
    }

    public void setLiquidationConsecutiveHits(int liquidationConsecutiveHits) {
        this.liquidationConsecutiveHits = liquidationConsecutiveHits;
    }

    public int getConditionalConsecutiveHits() {
        return conditionalConsecutiveHits;
    }

    public void setConditionalConsecutiveHits(int conditionalConsecutiveHits) {
        this.conditionalConsecutiveHits = conditionalConsecutiveHits;
    }

    public BigDecimal getLiquidationFeeBufferRate() {
        return liquidationFeeBufferRate;
    }

    public void setLiquidationFeeBufferRate(BigDecimal liquidationFeeBufferRate) {
        this.liquidationFeeBufferRate = liquidationFeeBufferRate;
    }
}
