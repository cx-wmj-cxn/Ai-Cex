package com.aicex.marketdata.service;

import com.aicex.marketdata.domain.MarketEvent;
import com.aicex.marketdata.domain.MarketEventType;
import com.aicex.marketdata.dto.KlineCandle;
import com.aicex.marketdata.dto.OrderBookSnapshot;
import com.aicex.marketdata.dto.TickerSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class MarketDataFacade {

    // Redis 作为跨实例共享热缓存，TTL 适中以降低陈旧数据风险。
    private static final Duration CACHE_TTL = Duration.ofSeconds(8);
    private static final String TICKER_SNAPSHOT_KEY_PREFIX = "market:snapshot:ticker:";

    private final TickerService tickerService;
    private final OrderBookService orderBookService;
    private final KlineService klineService;
    private final LocalCacheService localCacheService;
    private final RedisCacheService redisCacheService;
    private final MarketPushService pushService;
    private final MarketMetricsService metricsService;

    public MarketDataFacade(
            TickerService tickerService,
            OrderBookService orderBookService,
            KlineService klineService,
            LocalCacheService localCacheService,
            RedisCacheService redisCacheService,
            MarketPushService pushService,
            MarketMetricsService metricsService
    ) {
        this.tickerService = tickerService;
        this.orderBookService = orderBookService;
        this.klineService = klineService;
        this.localCacheService = localCacheService;
        this.redisCacheService = redisCacheService;
        this.pushService = pushService;
        this.metricsService = metricsService;
    }

    public void ingestEvent(MarketEvent event) {
        // 入口统一记录事件端到端延迟，便于监控实时性。
        metricsService.recordEventLatency(event.eventTime(), Instant.now());
        if (event.eventType() == MarketEventType.TRADE) {
            // 成交事件驱动 ticker + kline，并立即触发推送。
            tickerService.onTrade(event);
            klineService.onTrade(event);
            TickerSnapshot tickerSnapshot = tickerService.snapshot(event.symbol());
            writeTickerCache(tickerSnapshot);
            writeTickerSnapshot(tickerSnapshot);
            pushService.pushTicker(tickerSnapshot);
        } else if (event.eventType() == MarketEventType.ORDER_BOOK_DELTA) {
            // 深度增量事件仅更新订单簿，避免不必要计算。
            orderBookService.applyDelta(event);
            OrderBookSnapshot depth = orderBookService.snapshot(event.symbol(), 20);
            writeDepthCache(depth, 20);
            pushService.pushDepth(depth);
        }
        metricsService.recordEventProcess();
    }

    public TickerSnapshot queryTicker(String symbol) {
        String key = tickerKey(symbol);
        // 查询路径：本地缓存 -> Redis -> 实时计算，兼顾低延迟与高可用。
        return localCacheService.get(key, TickerSnapshot.class)
                .or(() -> redisCacheService.get(key, TickerSnapshot.class))
                .orElseGet(() -> {
                    TickerSnapshot snapshot = tickerService.snapshot(symbol);
                    writeTickerCache(snapshot);
                    return snapshot;
                });
    }

    public OrderBookSnapshot queryDepth(String symbol, int limit) {
        String key = depthKey(symbol, limit);
        return localCacheService.get(key, OrderBookSnapshot.class)
                .or(() -> redisCacheService.get(key, OrderBookSnapshot.class))
                .orElseGet(() -> {
                    OrderBookSnapshot snapshot = orderBookService.snapshot(symbol, limit);
                    writeDepthCache(snapshot, limit);
                    return snapshot;
                });
    }

    public List<KlineCandle> queryKline(String symbol, String interval, int limit) {
        return klineService.query(symbol, interval, limit);
    }

    private void writeTickerCache(TickerSnapshot snapshot) {
        String key = tickerKey(snapshot.symbol());
        // 双写缓存：进程内读取最优，Redis 用于多副本共享。
        localCacheService.put(key, snapshot);
        redisCacheService.put(key, snapshot, CACHE_TTL);
    }

    private void writeDepthCache(OrderBookSnapshot snapshot, int limit) {
        String key = depthKey(snapshot.symbol(), limit);
        localCacheService.put(key, snapshot);
        redisCacheService.put(key, snapshot, CACHE_TTL);
    }

    private void writeTickerSnapshot(TickerSnapshot snapshot) {
        redisCacheService.putPersistent(tickerSnapshotKey(snapshot.symbol()), snapshot);
    }

    private String tickerKey(String symbol) {
        return "market:ticker:" + symbol;
    }

    private String depthKey(String symbol, int limit) {
        return "market:depth:" + symbol + ":" + limit;
    }

    private String tickerSnapshotKey(String symbol) {
        return TICKER_SNAPSHOT_KEY_PREFIX + symbol;
    }
}
