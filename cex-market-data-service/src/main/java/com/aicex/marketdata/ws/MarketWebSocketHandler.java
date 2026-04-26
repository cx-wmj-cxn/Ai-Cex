package com.aicex.marketdata.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final MarketWebSocketSessionRegistry registry;

    public MarketWebSocketHandler(ObjectMapper objectMapper, MarketWebSocketSessionRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 约定客户端消息格式：{"action":"subscribe|unsubscribe","symbol":"BTCUSDT"}
        JsonNode root = objectMapper.readTree(message.getPayload());
        String action = root.path("action").asText();
        String symbol = root.path("symbol").asText();
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        if ("subscribe".equalsIgnoreCase(action)) {
            registry.subscribe(symbol, session);
        } else if ("unsubscribe".equalsIgnoreCase(action)) {
            registry.unsubscribe(symbol, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.removeSession(session);
    }
}
