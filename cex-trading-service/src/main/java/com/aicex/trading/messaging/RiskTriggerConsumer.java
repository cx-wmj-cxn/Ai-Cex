package com.aicex.trading.messaging;

import com.aicex.trading.domain.RiskTriggerEvent;
import com.aicex.trading.service.RiskTriggeredOrderExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "trading.risk-trigger.kafka", name = "enabled", havingValue = "true")
public class RiskTriggerConsumer {

    private final RiskTriggeredOrderExecutor orderExecutor;

    public RiskTriggerConsumer(RiskTriggeredOrderExecutor orderExecutor) {
        this.orderExecutor = orderExecutor;
    }

    @KafkaListener(
            topics = "${trading.risk-trigger.kafka.topic:risk.triggers}",
            groupId = "${trading.risk-trigger.kafka.group-id:trading-risk-trigger}"
    )
    public void consume(RiskTriggerEvent event) {
        orderExecutor.execute(event);
    }
}
