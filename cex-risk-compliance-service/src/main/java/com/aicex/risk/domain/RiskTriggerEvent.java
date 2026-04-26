package com.aicex.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskTriggerEvent(
        String triggerId,
        String accountId,
        String symbol,
        PositionSide positionSide,
        TriggerType triggerType,
        BigDecimal triggerPrice,
        BigDecimal markPrice,
        String reason,
        Instant triggeredAt
) {
}
