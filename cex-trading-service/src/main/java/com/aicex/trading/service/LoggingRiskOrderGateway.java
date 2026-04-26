package com.aicex.trading.service;

import com.aicex.trading.domain.RiskOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingRiskOrderGateway implements RiskOrderGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingRiskOrderGateway.class);

    @Override
    public void submit(RiskOrderRequest request) {
        // 占位适配器：后续可替换为交易引擎 HTTP/RPC 下单实现。
        log.warn(
                "Submit risk order request: requestId={}, accountId={}, symbol={}, action={}, triggerType={}, markPrice={}, triggerPrice={}, reduceOnly={}",
                request.requestId(),
                request.accountId(),
                request.symbol(),
                request.action(),
                request.triggerType(),
                request.markPrice(),
                request.triggerPrice(),
                request.reduceOnly()
        );
    }
}
