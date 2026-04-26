package com.aicex.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ConditionalOrderMonitorRule(
        String ruleId,
        String accountId,
        String symbol,
        PositionSide positionSide,
        TriggerType triggerType,
        BigDecimal triggerPrice,
        TriggerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public ConditionalOrderMonitorRule withStatus(TriggerStatus newStatus, Instant now) {
        return new ConditionalOrderMonitorRule(
                ruleId,
                accountId,
                symbol,
                positionSide,
                triggerType,
                triggerPrice,
                newStatus,
                createdAt,
                now
        );
    }

    public static ConditionalOrderMonitorRule newRule(
            String accountId,
            String symbol,
            PositionSide positionSide,
            TriggerType triggerType,
            BigDecimal triggerPrice,
            Instant now
    ) {
        return new ConditionalOrderMonitorRule(
                UUID.randomUUID().toString(),
                accountId,
                symbol,
                positionSide,
                triggerType,
                triggerPrice,
                TriggerStatus.ACTIVE,
                now,
                now
        );
    }
}
