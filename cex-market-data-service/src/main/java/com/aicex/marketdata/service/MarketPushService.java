package com.aicex.marketdata.service;

import com.aicex.marketdata.dto.OrderBookSnapshot;
import com.aicex.marketdata.dto.TickerSnapshot;
import com.aicex.marketdata.ws.MarketWebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MarketPushService {
    private static final long FLUSH_INTERVAL_MS = 100L;
    private static final CloseStatus BACKPRESSURE_CLOSE_STATUS = new CloseStatus(4008, "backpressure_overload");

    private final ObjectMapper objectMapper;
    private final MarketWebSocketSessionRegistry registry;
    private final ExecutorService sendExecutor;
    private final ScheduledExecutorService flushExecutor;
    private final ConcurrentHashMap<String, PendingPush> pendingByTopicSymbol = new ConcurrentHashMap<>();
    private final Counter wsOverflowCounter;
    private final Counter wsBackpressureDisconnectCounter;
    private final Counter wsCoalesceHitCounter;
    private final Counter wsFlushDispatchCounter;

    public MarketPushService(
            ObjectMapper objectMapper,
            MarketWebSocketSessionRegistry registry,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.sendExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor();
        this.flushExecutor.scheduleAtFixedRate(this::flushPending, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        this.wsOverflowCounter = meterRegistry.counter("market.ws.queue.overflow.total");
        this.wsBackpressureDisconnectCounter = meterRegistry.counter("market.ws.backpressure.disconnect.total");
        this.wsCoalesceHitCounter = meterRegistry.counter("market.ws.coalesce.hit.total");
        this.wsFlushDispatchCounter = meterRegistry.counter("market.ws.flush.dispatch.total");
    }

    public void pushTicker(TickerSnapshot tickerSnapshot) {
        push("ticker", tickerSnapshot.symbol(), Map.of("topic", "ticker", "payload", tickerSnapshot));
    }

    public void pushDepth(OrderBookSnapshot snapshot) {
        push("depth", snapshot.symbol(), Map.of("topic", "depth", "payload", snapshot));
    }

    private void push(String topic, String symbol, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = registry.sessions(symbol);
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            // symbol/topic 合并：高频更新只保留最新一条，按固定节拍下发。
            PendingPush previous = pendingByTopicSymbol.put(topic + ":" + symbol, new PendingPush(symbol, body));
            if (previous != null) {
                wsCoalesceHitCounter.increment();
            }
        } catch (Exception ignored) {
            // ignore single push failures and keep stream healthy
        }
    }

    private void flushPending() {
        if (pendingByTopicSymbol.isEmpty()) {
            return;
        }
        List<Map.Entry<String, PendingPush>> entries = new ArrayList<>(pendingByTopicSymbol.entrySet());
        for (Map.Entry<String, PendingPush> entry : entries) {
            if (!pendingByTopicSymbol.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            wsFlushDispatchCounter.increment();
            dispatch(entry.getValue().symbol(), entry.getValue().payload());
        }
    }

    private void dispatch(String symbol, String body) {
        Set<WebSocketSession> sessions = registry.sessions(symbol);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                registry.removeSession(session);
                continue;
            }
            // 先入队再异步发送，避免慢连接阻塞事件处理线程。
            boolean overflowed = registry.enqueue(session, body);
            if (overflowed) {
                wsOverflowCounter.increment();
            }
            if (overflowed && registry.shouldCloseForBackpressure(session)) {
                closeBackpressureSession(session);
                continue;
            }
            scheduleDrain(session);
        }
    }

    private void closeBackpressureSession(WebSocketSession session) {
        try {
            session.close(BACKPRESSURE_CLOSE_STATUS);
            wsBackpressureDisconnectCounter.increment();
        } catch (Exception ignored) {
            // ignore close failures and clear local state
        } finally {
            registry.removeSession(session);
        }
    }

    private void scheduleDrain(WebSocketSession session) {
        if (!registry.markSending(session)) {
            return;
        }
        sendExecutor.execute(() -> drainSession(session));
    }

    private void drainSession(WebSocketSession session) {
        try {
            while (session.isOpen()) {
                String payload = registry.poll(session);
                if (payload == null) {
                    break;
                }
                // 单连接串行发送，保障消息顺序且避免并发写 session。
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception ignored) {
            registry.removeSession(session);
        } finally {
            registry.markIdle(session);
            // 处理竞争窗口：释放发送标记后如果有新消息，立即补一次 drain。
            if (session.isOpen() && registry.hasPending(session)) {
                scheduleDrain(session);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        flushExecutor.shutdown();
        sendExecutor.shutdown();
    }

    private record PendingPush(String symbol, String payload) {
    }
}
