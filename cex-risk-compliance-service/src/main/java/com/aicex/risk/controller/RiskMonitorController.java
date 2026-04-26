package com.aicex.risk.controller;

import com.aicex.common.exception.BusinessException;
import com.aicex.common.exception.ErrorCode;
import com.aicex.common.model.ApiResponse;
import com.aicex.risk.domain.ConditionalOrderMonitorRule;
import com.aicex.risk.domain.PositionRiskSnapshot;
import com.aicex.risk.domain.PositionSide;
import com.aicex.risk.domain.TriggerType;
import com.aicex.risk.domain.TriggerStatus;
import com.aicex.risk.service.RiskMonitorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/risk/monitor")
public class RiskMonitorController {

    private final RiskMonitorService riskMonitorService;

    // 风险监控管理接口：接收上游仓位、价格、条件单数据并推送给监控服务。
    public RiskMonitorController(RiskMonitorService riskMonitorService) {
        this.riskMonitorService = riskMonitorService;
    }

    // 写入/更新仓位风险快照。事件驱动模式下，写入后会立即触发对应 symbol 的风险判定。
    @PostMapping("/positions")
    public ApiResponse<Void> upsertPosition(@Valid @RequestBody UpsertPositionRequest request) {
        riskMonitorService.upsertPosition(new PositionRiskSnapshot(
                request.accountId(),
                request.symbol(),
                request.positionSide(),
                request.positionQty(),
                request.markPrice(),
                request.walletBalance(),
                request.unrealizedPnl(),
                request.maintenanceMarginRate(),
                Instant.now()
        ));
        return ApiResponse.success(null);
    }

    // 写入/更新标记价格。价格变化后会立即驱动止盈、止损与强平评估。
    @PostMapping("/prices")
    public ApiResponse<Void> updatePrice(@Valid @RequestBody UpdateMarkPriceRequest request) {
        riskMonitorService.updateMarkPrice(request.symbol(), request.markPrice());
        return ApiResponse.success(null);
    }

    // 写入/更新条件单监控规则（仅止盈/止损）。
    @PostMapping("/conditional-rules")
    public ApiResponse<String> upsertConditionalRule(@Valid @RequestBody UpsertConditionalRuleRequest request) {
        // 强平由系统风险规则独立判定，不允许通过条件单接口直接创建。
        if (request.triggerType() == TriggerType.LIQUIDATION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.code(), "条件单仅支持止盈和止损触发");
        }
        ConditionalOrderMonitorRule rule = request.ruleId() == null || request.ruleId().isBlank()
                ? ConditionalOrderMonitorRule.newRule(
                request.accountId(),
                request.symbol(),
                request.positionSide(),
                request.triggerType(),
                request.triggerPrice(),
                Instant.now()
        )
                : new ConditionalOrderMonitorRule(
                request.ruleId(),
                request.accountId(),
                request.symbol(),
                request.positionSide(),
                request.triggerType(),
                request.triggerPrice(),
                TriggerStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
        riskMonitorService.upsertConditionalRule(rule);
        return ApiResponse.success(rule.ruleId());
    }

    // 仓位快照请求体：用于强平与条件单判定所需的最小账户状态。
    public record UpsertPositionRequest(
            @NotBlank String accountId,
            @NotBlank String symbol,
            @NotNull PositionSide positionSide,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal positionQty,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal markPrice,
            @NotNull BigDecimal walletBalance,
            @NotNull BigDecimal unrealizedPnl,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal maintenanceMarginRate
    ) {
    }

    // 标记价格请求体：作为风险判定优先价格源。
    public record UpdateMarkPriceRequest(
            @NotBlank String symbol,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal markPrice
    ) {
    }

    // 条件单规则请求体：支持新建（ruleId 为空）与覆盖更新（ruleId 非空）。
    public record UpsertConditionalRuleRequest(
            String ruleId,
            @NotBlank String accountId,
            @NotBlank String symbol,
            @NotNull PositionSide positionSide,
            @NotNull TriggerType triggerType,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal triggerPrice
    ) {
    }
}
