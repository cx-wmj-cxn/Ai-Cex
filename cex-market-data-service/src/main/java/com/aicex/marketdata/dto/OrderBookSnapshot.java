package com.aicex.marketdata.dto;

import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
        String symbol,
        List<DepthLevel> bids,
        List<DepthLevel> asks,
        long lastSequence,
        Instant updatedAt
) {
}
