package com.aicex.marketdata.dto;

import java.math.BigDecimal;

public record DepthLevel(BigDecimal price, BigDecimal quantity) {
}
