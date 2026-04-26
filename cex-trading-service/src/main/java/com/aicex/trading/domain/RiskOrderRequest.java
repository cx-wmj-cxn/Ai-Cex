package com.aicex.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskOrderRequest(
        String requestId,
        String accountId,
        String symbol,
        RiskOrderAction action,
        TriggerType triggerType,
        BigDecimal markPrice,
        BigDecimal triggerPrice,
        boolean reduceOnly,
        Instant requestedAt
) {
}
