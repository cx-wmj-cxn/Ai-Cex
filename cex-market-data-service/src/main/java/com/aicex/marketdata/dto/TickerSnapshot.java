package com.aicex.marketdata.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TickerSnapshot(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal indexPrice,
        BigDecimal open24h,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal volume24h,
        BigDecimal turnover24h,
        Instant updatedAt
) {
}
