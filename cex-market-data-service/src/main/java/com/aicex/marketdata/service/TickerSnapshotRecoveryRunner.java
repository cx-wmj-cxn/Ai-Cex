package com.aicex.marketdata.service;

import com.aicex.marketdata.dto.TickerSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class TickerSnapshotRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(TickerSnapshotRecoveryRunner.class);
    private static final String TICKER_SNAPSHOT_KEY_PREFIX = "market:snapshot:ticker:";

    private final RedisCacheService redisCacheService;
    private final TickerService tickerService;
    private final Timer restoreTimer;

    public TickerSnapshotRecoveryRunner(
            RedisCacheService redisCacheService,
            TickerService tickerService,
            MeterRegistry meterRegistry
    ) {
        this.redisCacheService = redisCacheService;
        this.tickerService = tickerService;
        this.restoreTimer = meterRegistry.timer("market.snapshot.restore.duration");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        restoreTimer.record(() -> {
            List<TickerSnapshot> snapshots = redisCacheService.listByPrefix(TICKER_SNAPSHOT_KEY_PREFIX, TickerSnapshot.class)
                    .stream()
                    .sorted(Comparator.comparing(TickerSnapshot::updatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            snapshots.forEach(tickerService::restoreSnapshot);
            log.info("Recovered {} ticker snapshots from redis.", snapshots.size());
        });
    }
}
