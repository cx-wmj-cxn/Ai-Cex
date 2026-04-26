package com.aicex.marketdata.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record KlineCandle(
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal turnover
) {
}
