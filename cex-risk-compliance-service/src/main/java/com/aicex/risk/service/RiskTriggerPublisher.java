package com.aicex.risk.service;

import com.aicex.risk.domain.RiskTriggerEvent;

public interface RiskTriggerPublisher {

    void publish(RiskTriggerEvent event);
}
