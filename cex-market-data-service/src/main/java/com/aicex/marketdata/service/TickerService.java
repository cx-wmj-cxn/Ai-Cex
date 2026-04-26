package com.aicex.marketdata.service;

import com.aicex.marketdata.domain.MarketEvent;
import com.aicex.marketdata.dto.TickerSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TickerService {

    // 按 symbol 维护独立状态，天然支持横向扩展与并行处理。
    private final Map<String, TickerState> states = new ConcurrentHashMap<>();

    public void onTrade(MarketEvent event) {
        if (event.tradePrice() == null || event.tradeQuantity() == null) {
            return;
        }
        states.computeIfAbsent(event.symbol(), k -> new TickerState()).onTrade(event);
    }

    public TickerSnapshot snapshot(String symbol) {
        TickerState state = states.get(symbol);
        if (state == null) {
            return new TickerSnapshot(
                    symbol,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Instant.EPOCH
            );
        }
        return state.snapshot(symbol);
    }

    public void restoreSnapshot(TickerSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null || snapshot.symbol().isBlank()) {
            return;
        }
        states.computeIfAbsent(snapshot.symbol(), ignored -> new TickerState()).restore(snapshot);
    }

    private static final class TickerState {
        private BigDecimal lastPrice = BigDecimal.ZERO;
        private BigDecimal indexPrice = BigDecimal.ZERO;
        private BigDecimal open24h = BigDecimal.ZERO;
        private BigDecimal high24h = BigDecimal.ZERO;
        private BigDecimal low24h = BigDecimal.ZERO;
        private BigDecimal volume24h = BigDecimal.ZERO;
        private BigDecimal turnover24h = BigDecimal.ZERO;
        private Instant windowStart = Instant.EPOCH;
        private Instant updatedAt = Instant.EPOCH;

        synchronized void onTrade(MarketEvent event) {
            if (windowStart.equals(Instant.EPOCH) || Duration.between(windowStart, event.eventTime()).toHours() >= 24) {
                resetWindow(event);
            }
            lastPrice = event.tradePrice();
            // 使用轻量 EMA 估算指数价，避免依赖重计算链路影响延迟。
            indexPrice = indexPrice.signum() == 0 ? lastPrice : indexPrice.multiply(new BigDecimal("0.98"))
                    .add(lastPrice.multiply(new BigDecimal("0.02")));
            high24h = high24h.signum() == 0 ? lastPrice : high24h.max(lastPrice);
            low24h = low24h.signum() == 0 ? lastPrice : low24h.min(lastPrice);
            volume24h = volume24h.add(event.tradeQuantity());
            turnover24h = turnover24h.add(event.tradePrice().multiply(event.tradeQuantity()));
            updatedAt = event.eventTime();
        }

        synchronized TickerSnapshot snapshot(String symbol) {
            return new TickerSnapshot(symbol, lastPrice, indexPrice, open24h, high24h, low24h, volume24h, turnover24h, updatedAt);
        }

        synchronized void restore(TickerSnapshot snapshot) {
            Instant restoredAt = snapshot.updatedAt() == null ? Instant.EPOCH : snapshot.updatedAt();
            if (restoredAt.isBefore(updatedAt)) {
                return;
            }
            lastPrice = valueOrZero(snapshot.lastPrice());
            indexPrice = valueOrZero(snapshot.indexPrice());
            open24h = valueOrZero(snapshot.open24h());
            high24h = valueOrZero(snapshot.high24h());
            low24h = valueOrZero(snapshot.low24h());
            volume24h = valueOrZero(snapshot.volume24h());
            turnover24h = valueOrZero(snapshot.turnover24h());
            updatedAt = restoredAt;
            windowStart = restoredAt;
        }

        private BigDecimal valueOrZero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private void resetWindow(MarketEvent event) {
            windowStart = event.eventTime();
            open24h = event.tradePrice();
            high24h = event.tradePrice();
            low24h = event.tradePrice();
            volume24h = BigDecimal.ZERO;
            turnover24h = BigDecimal.ZERO;
        }
    }
}
