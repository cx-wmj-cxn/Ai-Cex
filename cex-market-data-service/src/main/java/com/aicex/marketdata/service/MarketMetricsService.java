package com.aicex.marketdata.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class MarketMetricsService {

    private final Timer eventLagTimer;
    private final Counter eventCounter;
    private volatile Instant lastEventAt = Instant.EPOCH;

    public MarketMetricsService(MeterRegistry meterRegistry) {
        this.eventLagTimer = meterRegistry.timer("market.event.lag");
        this.eventCounter = meterRegistry.counter("market.event.processed.total");
    }

    public void recordEventLatency(Instant eventTime, Instant now) {
        if (eventTime != null) {
            // 记录事件时间与处理时间差，用于延迟 SLA 观测。
            long lag = Math.max(Duration.between(eventTime, now).toMillis(), 0);
            eventLagTimer.record(Duration.ofMillis(lag));
        }
        lastEventAt = now;
    }

    public void recordEventProcess() {
        eventCounter.increment();
    }

    public Instant lastEventAt() {
        return lastEventAt;
    }
}
