package com.aicex.risk.service;

import com.aicex.risk.domain.ConditionalOrderMonitorRule;
import com.aicex.risk.domain.PositionRiskSnapshot;
import com.aicex.risk.domain.TriggerStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RiskMonitorStateStore {

    private final Map<String, PositionRiskSnapshot> positions = new ConcurrentHashMap<>();
    private final Map<String, ConditionalOrderMonitorRule> conditionalRules = new ConcurrentHashMap<>();
    private final Map<String, Integer> triggerCounters = new ConcurrentHashMap<>();

    public void upsertPosition(PositionRiskSnapshot snapshot) {
        positions.put(snapshot.positionKey(), snapshot);
    }

    public Collection<PositionRiskSnapshot> allPositions() {
        return positions.values();
    }

    public Optional<PositionRiskSnapshot> findPosition(String positionKey) {
        return Optional.ofNullable(positions.get(positionKey));
    }

    public void upsertConditionalRule(ConditionalOrderMonitorRule rule) {
        conditionalRules.put(rule.ruleId(), rule);
    }

    public Collection<ConditionalOrderMonitorRule> activeConditionalRules() {
        return conditionalRules.values().stream()
                .filter(rule -> rule.status() == TriggerStatus.ACTIVE)
                .toList();
    }

    public void markRuleTriggered(String ruleId, ConditionalOrderMonitorRule updated) {
        conditionalRules.put(ruleId, updated);
    }

    public int incrementCounter(String key) {
        return triggerCounters.merge(key, 1, Integer::sum);
    }

    public void resetCounter(String key) {
        triggerCounters.remove(key);
    }
}
