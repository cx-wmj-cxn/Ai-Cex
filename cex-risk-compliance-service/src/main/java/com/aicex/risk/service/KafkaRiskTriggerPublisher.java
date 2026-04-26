package com.aicex.risk.service;

import com.aicex.risk.config.RiskKafkaProperties;
import com.aicex.risk.domain.RiskTriggerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "risk.monitor.kafka", name = "enabled", havingValue = "true")
public class KafkaRiskTriggerPublisher implements RiskTriggerPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaRiskTriggerPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RiskKafkaProperties properties;

    public KafkaRiskTriggerPublisher(KafkaTemplate<String, Object> kafkaTemplate, RiskKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(RiskTriggerEvent event) {
        kafkaTemplate.send(properties.getTopic(), event.accountId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish risk trigger event, triggerId={}, topic={}", event.triggerId(), properties.getTopic(), ex);
                        return;
                    }
                    log.info(
                            "Published risk trigger event: triggerId={}, topic={}, partition={}, offset={}",
                            event.triggerId(),
                            properties.getTopic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                });
    }
}
