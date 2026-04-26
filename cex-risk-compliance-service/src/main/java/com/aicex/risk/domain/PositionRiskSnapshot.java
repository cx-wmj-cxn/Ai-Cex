package com.aicex.risk.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionRiskSnapshot(
        String accountId,
        String symbol,
        PositionSide positionSide,
        BigDecimal positionQty,
        BigDecimal markPrice,
        BigDecimal walletBalance,
        BigDecimal unrealizedPnl,
        BigDecimal maintenanceMarginRate,
        Instant updatedAt
) {
    public String positionKey() {
        return accountId + ":" + symbol + ":" + positionSide;
    }
}
