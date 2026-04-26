package com.aicex.trading.service;

import com.aicex.trading.domain.RiskOrderRequest;

public interface RiskOrderGateway {

    void submit(RiskOrderRequest request);
}
