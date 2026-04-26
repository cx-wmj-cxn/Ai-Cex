package com.aicex.marketdata.health;

import com.aicex.marketdata.service.MarketMetricsService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MarketDataHealthIndicator implements HealthIndicator {

    private static final Duration STALE_THRESHOLD = Duration.ofSeconds(30);

    private final MarketMetricsService marketMetricsService;

    public MarketDataHealthIndicator(MarketMetricsService marketMetricsService) {
        this.marketMetricsService = marketMetricsService;
    }

    @Override
    public Health health() {
        Instant lastEventAt = marketMetricsService.lastEventAt();
        Duration staleness = Duration.between(lastEventAt, Instant.now());
        // 使用“数据新鲜度”作为可用性信号，避免仅进程存活却无行情流入。
        if (lastEventAt.equals(Instant.EPOCH) || staleness.compareTo(STALE_THRESHOLD) > 0) {
            return Health.down()
                    .withDetail("lastEventAt", lastEventAt.toString())
                    .withDetail("stalenessSeconds", staleness.toSeconds())
                    .build();
        }
        return Health.up()
                .withDetail("lastEventAt", lastEventAt.toString())
                .withDetail("stalenessMillis", staleness.toMillis())
                .build();
    }
}
