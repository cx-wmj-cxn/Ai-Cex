package com.aicex.risk.service;

import com.aicex.risk.domain.RiskTriggerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RiskTriggerPublisher.class)
public class LoggingRiskTriggerPublisher implements RiskTriggerPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingRiskTriggerPublisher.class);

    @Override
    public void publish(RiskTriggerEvent event) {
        log.warn(
                "Risk trigger fired: triggerId={}, accountId={}, symbol={}, side={}, type={}, triggerPrice={}, markPrice={}, reason={}",
                event.triggerId(),
                event.accountId(),
                event.symbol(),
                event.positionSide(),
                event.triggerType(),
                event.triggerPrice(),
                event.markPrice(),
                event.reason()
        );
    }
}
