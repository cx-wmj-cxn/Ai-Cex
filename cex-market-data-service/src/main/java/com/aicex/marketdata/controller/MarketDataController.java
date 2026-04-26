package com.aicex.marketdata.controller;

import com.aicex.common.model.ApiResponse;
import com.aicex.marketdata.dto.KlineCandle;
import com.aicex.marketdata.dto.OrderBookSnapshot;
import com.aicex.marketdata.dto.TickerSnapshot;
import com.aicex.marketdata.service.MarketDataFacade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final MarketDataFacade marketDataFacade;

    public MarketDataController(MarketDataFacade marketDataFacade) {
        this.marketDataFacade = marketDataFacade;
    }

    @GetMapping("/ticker/{symbol}")
    public ApiResponse<TickerSnapshot> ticker(@PathVariable @NotBlank String symbol) {
        return ApiResponse.success(marketDataFacade.queryTicker(symbol));
    }

    @GetMapping("/depth/{symbol}")
    public ApiResponse<OrderBookSnapshot> depth(
            @PathVariable @NotBlank String symbol,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        return ApiResponse.success(marketDataFacade.queryDepth(symbol, limit));
    }

    @GetMapping("/kline/{symbol}")
    public ApiResponse<List<KlineCandle>> kline(
            @PathVariable @NotBlank String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit
    ) {
        return ApiResponse.success(marketDataFacade.queryKline(symbol, interval, limit));
    }

    @GetMapping("/overview/{symbol}")
    public ApiResponse<Map<String, Object>> overview(@PathVariable @NotBlank String symbol) {
        // 聚合视图接口，减少前端多次请求带来的额外 RTT。
        TickerSnapshot ticker = marketDataFacade.queryTicker(symbol);
        OrderBookSnapshot depth = marketDataFacade.queryDepth(symbol, 20);
        return ApiResponse.success(Map.of(
                "ticker", ticker,
                "depth", depth
        ));
    }
}
