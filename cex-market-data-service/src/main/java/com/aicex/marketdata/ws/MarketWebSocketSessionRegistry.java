package com.aicex.marketdata.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class    MarketWebSocketSessionRegistry {

    private static final int MAX_OUTBOUND_QUEUE_SIZE = 200;
    private static final int MAX_CONSECUTIVE_OVERFLOW = 3;

    // symbol 维度会话桶，便于精准推送并减少无效广播。
    private final ConcurrentHashMap<String, Set<WebSocketSession>> symbolSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, SessionChannel> sessionChannels = new ConcurrentHashMap<>();

    public void subscribe(String symbol, WebSocketSession session) {
        symbolSessions.computeIfAbsent(symbol, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        sessionChannels.computeIfAbsent(session, ignored -> new SessionChannel());
    }

    public void unsubscribe(String symbol, WebSocketSession session) {
        Set<WebSocketSession> sessions = symbolSessions.get(symbol);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
    }

    public Set<WebSocketSession> sessions(String symbol) {
        return symbolSessions.getOrDefault(symbol, Set.of());
    }

    public void removeSession(WebSocketSession session) {
        symbolSessions.values().forEach(s -> s.remove(session));
        sessionChannels.remove(session);
    }

    public boolean enqueue(WebSocketSession session, String payload) {
        SessionChannel channel = sessionChannels.computeIfAbsent(session, ignored -> new SessionChannel());
        return channel.enqueue(payload);
    }

    public String poll(WebSocketSession session) {
        SessionChannel channel = sessionChannels.get(session);
        if (channel == null) {
            return null;
        }
        return channel.poll();
    }

    public boolean markSending(WebSocketSession session) {
        SessionChannel channel = sessionChannels.get(session);
        return channel != null && channel.markSending();
    }

    public void markIdle(WebSocketSession session) {
        SessionChannel channel = sessionChannels.get(session);
        if (channel != null) {
            channel.markIdle();
        }
    }

    public boolean hasPending(WebSocketSession session) {
        SessionChannel channel = sessionChannels.get(session);
        return channel != null && channel.hasPending();
    }

    public boolean shouldCloseForBackpressure(WebSocketSession session) {
        SessionChannel channel = sessionChannels.get(session);
        return channel != null && channel.consecutiveOverflows() >= MAX_CONSECUTIVE_OVERFLOW;
    }

    private static final class SessionChannel {
        private final ArrayDeque<String> outbound = new ArrayDeque<>();
        private final AtomicBoolean sending = new AtomicBoolean(false);
        private int consecutiveOverflows;

        synchronized boolean enqueue(String payload) {
            if (outbound.size() >= MAX_OUTBOUND_QUEUE_SIZE) {
                // 队列满时丢弃最旧消息，优先保留最新行情。
                outbound.pollFirst();
                consecutiveOverflows++;
                outbound.offerLast(payload);
                return true;
            }
            outbound.offerLast(payload);
            consecutiveOverflows = 0;
            return false;
        }

        synchronized String poll() {
            return outbound.pollFirst();
        }

        synchronized boolean hasPending() {
            return !outbound.isEmpty();
        }

        synchronized int consecutiveOverflows() {
            return consecutiveOverflows;
        }

        boolean markSending() {
            return sending.compareAndSet(false, true);
        }

        void markIdle() {
            sending.set(false);
        }
    }
}
