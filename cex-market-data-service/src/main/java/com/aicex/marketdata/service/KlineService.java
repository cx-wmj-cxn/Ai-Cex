package com.aicex.marketdata.service;

import com.aicex.marketdata.domain.MarketEvent;
import com.aicex.marketdata.dto.KlineCandle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KlineService {

    private static final Map<String, Integer> INTERVAL_SECONDS = Map.of(
            "1m", 60,
            "5m", 300,
            "15m", 900,
            "1h", 3600,
            "4h", 14400,
            "1d", 86400
    );
    private static final int MAX_CANDLES = 1000;

    // symbol -> interval -> series，按维度隔离降低高并发下的竞争。
    private final Map<String, Map<String, SeriesState>> symbolSeries = new ConcurrentHashMap<>();

    public void onTrade(MarketEvent event) {
        Map<String, SeriesState> seriesMap = symbolSeries.computeIfAbsent(event.symbol(), ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Integer> interval : INTERVAL_SECONDS.entrySet()) {
            SeriesState state = seriesMap.computeIfAbsent(interval.getKey(), k -> new SeriesState(interval.getValue()));
            state.onTrade(event, interval.getKey());
        }
    }

    public List<KlineCandle> query(String symbol, String interval, int limit) {
        Map<String, SeriesState> seriesMap = symbolSeries.get(symbol);
        if (seriesMap == null) {
            return List.of();
        }
        SeriesState state = seriesMap.get(interval);
        if (state == null) {
            return List.of();
        }
        return state.latest(symbol, interval, limit);
    }

    private static final class SeriesState {
        private final int stepSeconds;
        private final Deque<KlineCandle> history = new ArrayDeque<>();
        private KlineCandle current;

        SeriesState(int stepSeconds) {
            this.stepSeconds = stepSeconds;
        }

        synchronized void onTrade(MarketEvent event, String interval) {
            if (event.tradePrice() == null || event.tradeQuantity() == null) {
                return;
            }
            // 时间对齐到周期边界，保证各实例生成 K 线一致。
            Instant openTime = align(event.eventTime(), stepSeconds);
            Instant closeTime = openTime.plus(stepSeconds, ChronoUnit.SECONDS);
            if (current == null || !current.openTime().equals(openTime)) {
                flushCurrent();
                current = new KlineCandle(
                        event.symbol(),
                        interval,
                        openTime,
                        closeTime,
                        event.tradePrice(),
                        event.tradePrice(),
                        event.tradePrice(),
                        event.tradePrice(),
                        event.tradeQuantity(),
                        event.tradePrice().multiply(event.tradeQuantity())
                );
                return;
            }
            current = new KlineCandle(
                    current.symbol(),
                    current.interval(),
                    current.openTime(),
                    current.closeTime(),
                    current.open(),
                    current.high().max(event.tradePrice()),
                    current.low().min(event.tradePrice()),
                    event.tradePrice(),
                    current.volume().add(event.tradeQuantity()),
                    current.turnover().add(event.tradePrice().multiply(event.tradeQuantity()))
            );
        }

        synchronized List<KlineCandle> latest(String symbol, String interval, int limit) {
            List<KlineCandle> all = new ArrayList<>(history);
            if (current != null) {
                all.add(current);
            }
            int fromIndex = Math.max(0, all.size() - limit);
            return all.subList(fromIndex, all.size())
                    .stream()
                    .filter(c -> c.symbol().equals(symbol) && c.interval().equals(interval))
                    .toList();
        }

        private void flushCurrent() {
            if (current == null) {
                return;
            }
            history.addLast(current);
            // 有界历史避免内存无限增长。
            if (history.size() > MAX_CANDLES) {
                history.removeFirst();
            }
            current = null;
        }

        private Instant align(Instant time, int stepSeconds) {
            long epoch = time.getEpochSecond();
            return Instant.ofEpochSecond(epoch - (epoch % stepSeconds));
        }
    }
}
