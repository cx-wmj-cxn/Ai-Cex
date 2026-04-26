package com.aicex.risk.service;

import com.aicex.risk.config.RiskMonitorProperties;
import com.aicex.risk.domain.ConditionalOrderMonitorRule;
import com.aicex.risk.domain.PositionRiskSnapshot;
import com.aicex.risk.domain.PositionSide;
import com.aicex.risk.domain.RiskTriggerEvent;
import com.aicex.risk.domain.TriggerStatus;
import com.aicex.risk.domain.TriggerType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RiskMonitorService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RiskMonitorStateStore stateStore;
    private final RiskTriggerPublisher triggerPublisher;
    private final RiskMonitorProperties properties;
    private final Map<String, BigDecimal> markPrices = new ConcurrentHashMap<>();
    private final Timer monitorLoopTimer;
    private final Counter liquidationTriggerCounter;
    private final Counter conditionalTriggerCounter;

    // 统一监控入口：由行情/仓位/规则变更事件驱动触发判定。
    public RiskMonitorService(
            RiskMonitorStateStore stateStore,
            RiskTriggerPublisher triggerPublisher,
            RiskMonitorProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.stateStore = stateStore;
        this.triggerPublisher = triggerPublisher;
        this.properties = properties;
        this.monitorLoopTimer = meterRegistry.timer("risk.monitor.loop.duration");
        this.liquidationTriggerCounter = meterRegistry.counter("risk.monitor.trigger.count", "type", TriggerType.LIQUIDATION.name());
        this.conditionalTriggerCounter = meterRegistry.counter("risk.monitor.trigger.count", "type", "CONDITIONAL");
    }

    // 事件驱动扫描入口：按 symbol 聚焦扫描，降低全量扫描频率。
    private void monitorBySymbol(String symbol) {
        monitorLoopTimer.record(() -> {
            Collection<PositionRiskSnapshot> positions = stateStore.allPositions();
            positions.stream()
                    .filter(position -> position.symbol().equals(symbol))
                    .forEach(this::evaluateLiquidation);
            stateStore.activeConditionalRules().stream()
                    .filter(rule -> rule.symbol().equals(symbol))
                    .forEach(this::evaluateConditionalRule);
        });
    }

    // 上游仓位快照写入入口；仓位变化后立即评估对应 symbol 的强平与条件单。
    public void upsertPosition(PositionRiskSnapshot snapshot) {
        stateStore.upsertPosition(snapshot);
        monitorBySymbol(snapshot.symbol());
    }

    // 上游标记价格写入入口；价格变化后立即触发对应 symbol 的风险判定。
    public void updateMarkPrice(String symbol, BigDecimal price) {
        markPrices.put(symbol, price);
        monitorBySymbol(symbol);
    }

    // 条件单规则写入入口（止盈/止损）；规则变化后立即进行一次命中判定。
    public void upsertConditionalRule(ConditionalOrderMonitorRule rule) {
        stateStore.upsertConditionalRule(rule);
        monitorBySymbol(rule.symbol());
    }

    // 强平判定：
    // equity <= maintenanceRequirement 时累积命中计数，达到阈值后触发强平事件。
    private void evaluateLiquidation(PositionRiskSnapshot snapshot) {
        BigDecimal markPrice = resolveMarkPrice(snapshot.symbol(), snapshot.markPrice());
        if (markPrice == null || snapshot.positionQty() == null || snapshot.positionQty().signum() == 0) {
            return;
        }
        // 仓位名义价值 = |仓位数量| * 标记价格。
        BigDecimal positionNotional = snapshot.positionQty().abs().multiply(markPrice);
        // 费用缓冲，用于覆盖强平执行时的预估交易成本。
        BigDecimal feeBuffer = positionNotional.multiply(properties.getLiquidationFeeBufferRate());
        // 维持保证金需求 = 仓位名义价值 * 维持保证金率 + 费用缓冲。
        BigDecimal maintenanceRequirement = positionNotional.multiply(valueOrZero(snapshot.maintenanceMarginRate())).add(feeBuffer);
        // 账户权益 = 钱包余额 + 未实现盈亏。
        BigDecimal equity = valueOrZero(snapshot.walletBalance()).add(valueOrZero(snapshot.unrealizedPnl()));
        String counterKey = "LIQ:" + snapshot.positionKey();
        if (equity.compareTo(maintenanceRequirement) <= 0) {
            // 连续命中防抖：避免短时价格抖动导致误触发。
            int hits = stateStore.incrementCounter(counterKey);
            if (hits >= properties.getLiquidationConsecutiveHits()) {
                stateStore.resetCounter(counterKey);
                publish(
                        new RiskTriggerEvent(
                                UUID.randomUUID().toString(),
                                snapshot.accountId(),
                                snapshot.symbol(),
                                snapshot.positionSide(),
                                TriggerType.LIQUIDATION,
                                maintenanceRequirement,
                                markPrice,
                                "equity below maintenance requirement",
                                Instant.now()
                        )
                );
                liquidationTriggerCounter.increment();
            }
            return;
        }
        // 条件恢复正常时清空计数，重新统计连续命中次数。
        stateStore.resetCounter(counterKey);
    }

    // 条件单判定（止盈/止损）：
    // 命中后同样走连续命中防抖，触发后将规则状态改为 TRIGGERED。
    private void evaluateConditionalRule(ConditionalOrderMonitorRule rule) {
        String positionKey = rule.accountId() + ":" + rule.symbol() + ":" + rule.positionSide();
        PositionRiskSnapshot snapshot = stateStore.findPosition(positionKey).orElse(null);
        if (snapshot == null) {
            return;
        }
        BigDecimal markPrice = resolveMarkPrice(rule.symbol(), snapshot.markPrice());
        if (markPrice == null || rule.triggerPrice() == null) {
            return;
        }
        boolean hit = isConditionalTriggered(rule.triggerType(), rule.positionSide(), markPrice, rule.triggerPrice());
        String counterKey = "COND:" + rule.ruleId();
        if (!hit) {
            stateStore.resetCounter(counterKey);
            return;
        }
        int hits = stateStore.incrementCounter(counterKey);
        if (hits < properties.getConditionalConsecutiveHits()) {
            return;
        }
        stateStore.resetCounter(counterKey);
        ConditionalOrderMonitorRule triggered = rule.withStatus(TriggerStatus.TRIGGERED, Instant.now());
        stateStore.markRuleTriggered(rule.ruleId(), triggered);
        publish(
                new RiskTriggerEvent(
                        rule.ruleId(),
                        rule.accountId(),
                        rule.symbol(),
                        rule.positionSide(),
                        rule.triggerType(),
                        rule.triggerPrice(),
                        markPrice,
                        "conditional order trigger hit",
                        Instant.now()
                )
        );
        conditionalTriggerCounter.increment();
    }

    // 方向性触发规则：
    // LONG: 止盈 mark >= trigger，止损 mark <= trigger
    // SHORT: 止盈 mark <= trigger，止损 mark >= trigger
    private boolean isConditionalTriggered(
            TriggerType triggerType,
            PositionSide side,
            BigDecimal markPrice,
            BigDecimal triggerPrice
    ) {
        return switch (triggerType) {
            case TAKE_PROFIT -> side == PositionSide.LONG
                    ? markPrice.compareTo(triggerPrice) >= 0
                    : markPrice.compareTo(triggerPrice) <= 0;
            case STOP_LOSS -> side == PositionSide.LONG
                    ? markPrice.compareTo(triggerPrice) <= 0
                    : markPrice.compareTo(triggerPrice) >= 0;
            case LIQUIDATION -> false;
        };
    }

    // 优先使用实时行情写入的标记价；缺失时回退到仓位快照价格。
    private BigDecimal resolveMarkPrice(String symbol, BigDecimal snapshotPrice) {
        return markPrices.getOrDefault(symbol, snapshotPrice);
    }

    // 空值保护，避免判定链路中的空指针影响监控主流程。
    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    // 统一事件发布出口，便于后续切换 Kafka / 其他消息中间件。
    private void publish(RiskTriggerEvent event) {
        triggerPublisher.publish(event);
    }
}
