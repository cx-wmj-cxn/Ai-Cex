package com.aicex.marketdata.service;

import com.aicex.marketdata.domain.MarketEvent;
import com.aicex.marketdata.dto.DepthLevel;
import com.aicex.marketdata.dto.OrderBookSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
public class OrderBookService {

    // 按交易对拆分订单簿，减少锁冲突，提升并发更新能力。
    private final Map<String, SymbolOrderBook> books = new ConcurrentHashMap<>();

    public void applyDelta(MarketEvent event) {
        SymbolOrderBook book = books.computeIfAbsent(event.symbol(), s -> new SymbolOrderBook());
        book.apply(event);
    }

    public OrderBookSnapshot snapshot(String symbol, int limit) {
        SymbolOrderBook book = books.get(symbol);
        if (book == null) {
            return new OrderBookSnapshot(symbol, List.of(), List.of(), 0L, Instant.EPOCH);
        }
        return book.snapshot(symbol, limit);
    }

    private static final class SymbolOrderBook {
        // 价格有序结构，便于 O(logN) 更新与按档位截取。
        private final ConcurrentSkipListMap<BigDecimal, BigDecimal> bids =
                new ConcurrentSkipListMap<>(Comparator.reverseOrder());
        private final ConcurrentSkipListMap<BigDecimal, BigDecimal> asks =
                new ConcurrentSkipListMap<>();
        private volatile long lastSequence;
        private volatile Instant updatedAt = Instant.EPOCH;

        synchronized void apply(MarketEvent event) {
            // 同一交易对内串行应用增量，保证顺序一致性。
            upsert(bids, event.bidPrice(), event.bidQuantity());
            upsert(asks, event.askPrice(), event.askQuantity());
            this.lastSequence = Math.max(this.lastSequence, event.sequence());
            this.updatedAt = event.eventTime();
        }

        synchronized OrderBookSnapshot snapshot(String symbol, int limit) {
            return new OrderBookSnapshot(
                    symbol,
                    topLevels(bids, limit),
                    topLevels(asks, limit),
                    lastSequence,
                    updatedAt
            );
        }

        private void upsert(ConcurrentSkipListMap<BigDecimal, BigDecimal> side, BigDecimal price, BigDecimal qty) {
            if (price == null || qty == null) {
                return;
            }
            // 0 或负数数量视为删除档位，兼容常见增量协议。
            if (qty.signum() <= 0) {
                side.remove(price);
            } else {
                side.put(price, qty);
            }
        }

        private List<DepthLevel> topLevels(ConcurrentSkipListMap<BigDecimal, BigDecimal> side, int limit) {
            List<DepthLevel> levels = new ArrayList<>(Math.max(limit, 0));
            int index = 0;
            for (Map.Entry<BigDecimal, BigDecimal> entry : side.entrySet()) {
                if (index++ >= limit) {
                    break;
                }
                levels.add(new DepthLevel(entry.getKey(), entry.getValue()));
            }
            return levels;
        }
    }
}
