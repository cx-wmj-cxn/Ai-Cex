package com.aicex.marketdata.config;

import com.aicex.marketdata.ws.MarketWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketWebSocketHandler marketWebSocketHandler;

    public WebSocketConfig(MarketWebSocketHandler marketWebSocketHandler) {
        this.marketWebSocketHandler = marketWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 公共行情频道入口，生产环境建议进一步收敛允许来源。
        registry.addHandler(marketWebSocketHandler, "/ws/market")
                .setAllowedOriginPatterns("*");
    }
}
