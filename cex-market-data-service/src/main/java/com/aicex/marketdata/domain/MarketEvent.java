package com.aicex.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketEvent(
        String symbol,
        MarketEventType eventType,
        BigDecimal tradePrice,
        BigDecimal tradeQuantity,
        BigDecimal bidPrice,
        BigDecimal bidQuantity,
        BigDecimal askPrice,
        BigDecimal askQuantity,
        Instant eventTime,
        long sequence
) {
}
