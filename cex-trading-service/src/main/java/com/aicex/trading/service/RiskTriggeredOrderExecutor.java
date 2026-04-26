package com.aicex.trading.service;

import com.aicex.trading.domain.RiskTriggerEvent;
import com.aicex.trading.config.RiskTriggerExecutionProperties;
import com.aicex.trading.domain.PositionSide;
import com.aicex.trading.domain.RiskOrderAction;
import com.aicex.trading.domain.RiskOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskTriggeredOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(RiskTriggeredOrderExecutor.class);
    private final Map<String, Instant> seenTriggers = new ConcurrentHashMap<>();
    private final RiskOrderGateway riskOrderGateway;
    private final RiskTriggerExecutionProperties properties;

    public RiskTriggeredOrderExecutor(RiskOrderGateway riskOrderGateway, RiskTriggerExecutionProperties properties) {
        this.riskOrderGateway = riskOrderGateway;
        this.properties = properties;
    }

    public void execute(RiskTriggerEvent event) {
        if (event == null || event.triggerId() == null || event.triggerId().isBlank()) {
            log.warn("Skip risk trigger execution due to empty trigger id.");
            return;
        }
        cleanupExpiredKeys();
        Instant now = Instant.now();
        Instant existing = seenTriggers.putIfAbsent(event.triggerId(), now);
        if (existing != null) {
            log.info("Skip duplicated risk trigger: triggerId={}, firstSeenAt={}", event.triggerId(), existing);
            return;
        }
        RiskOrderRequest request = toRiskOrderRequest(event, now);
        riskOrderGateway.submit(request);
    }

    private RiskOrderRequest toRiskOrderRequest(RiskTriggerEvent event, Instant now) {
        RiskOrderAction action = event.positionSide() == PositionSide.LONG
                ? RiskOrderAction.CLOSE_LONG
                : RiskOrderAction.CLOSE_SHORT;
        return new RiskOrderRequest(
                event.triggerId(),
                event.accountId(),
                event.symbol(),
                action,
                event.triggerType(),
                event.markPrice(),
                event.triggerPrice(),
                true,
                now
        );
    }

    private void cleanupExpiredKeys() {
        Instant expireBefore = Instant.now().minusSeconds(properties.getIdempotencyWindowSeconds());
        seenTriggers.entrySet().removeIf(entry -> entry.getValue().isBefore(expireBefore));
    }
}
