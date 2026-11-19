package com.smartparking.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.order.client.AuditClient;
import com.smartparking.order.dto.BillingRuleDTO;
import com.smartparking.order.po.BillingRule;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.BillingRuleService;
import com.smartparking.order.service.ParkingOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 计费规则管理端接口（新增 / 修改 / 删除）。
 *
 * <p>只读列表沿用 {@code GET /api/order/billing-rules}（{@link ParkingOrderController}），
 * 本控制器只补写操作。计费规则直接决定应收金额，属资损高危接口：仅管理员（角色 1/2）可调，
 * 且每次变更都写审计日志，便于事后追溯「谁在什么时候把单价从 5 元改成 2 元」。</p>
 */
@Tag(name = "计费规则管理", description = "计费规则增删改（管理员）")
@RestController
@RequestMapping("/api/order/billing-rules")
@RequiredArgsConstructor
public class BillingRuleController {

    private final BillingRuleService billingRuleService;
    private final ParkingOrderService parkingOrderService;
    private final AuditClient auditClient;

    @Operation(summary = "新增计费规则")
    @PostMapping
    public Result<BillingRule> create(@RequestBody @Valid BillingRuleDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        validate(dto);
        BillingRule rule = new BillingRule();
        apply(rule, dto);
        billingRuleService.save(rule);
        recordAudit(currentUserId, "新增计费规则", rule);
        return Result.success(rule);
    }

    @Operation(summary = "修改计费规则")
    @PutMapping("/{id}")
    public Result<BillingRule> update(@Parameter(description = "规则ID") @PathVariable Long id,
            @RequestBody @Valid BillingRuleDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        BillingRule exist = billingRuleService.getById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "计费规则不存在或已删除");
        }
        validate(dto);
        apply(exist, dto);
        billingRuleService.updateById(exist);
        recordAudit(currentUserId, "修改计费规则", exist);
        return Result.success(exist);
    }

    @Operation(summary = "删除计费规则（有进行中订单引用时拒绝）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "规则ID") @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        BillingRule exist = billingRuleService.getById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "计费规则不存在或已删除");
        }
        // 进行中订单仍引用该规则时要能算出应收金额，删了会让结算直接失败（宁可拒删也不让门岗堵车）
        long ongoing = parkingOrderService.count(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getBillingRuleId, id)
                .eq(ParkingOrder::getStatus, 0));
        if (ongoing > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "该规则仍被 " + ongoing + " 笔进行中订单使用，请等这些车辆出场后再删除");
        }
        billingRuleService.removeById(id);
        recordAudit(currentUserId, "删除计费规则 " + exist.getRuleName(), exist);
        return Result.success();
    }

    private void requireAdmin(String role) {
        if (!"1".equals(role) && !"2".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "计费规则仅管理员可维护");
        }
    }

    /**
     * 跨字段校验：单字段注解管不到的语义约束放这里。
     */
    private void validate(BillingRuleDTO dto) {
        if (Integer.valueOf(2).equals(dto.getVehicleType()) && dto.getMonthlyPrice() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月租车规则必须填写月租价格");
        }
        if (dto.getDailyCap() != null && dto.getDailyCap().compareTo(dto.getUnitPrice()) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "日封顶（" + dto.getDailyCap() + "）不能低于一个计费单位的价格（" + dto.getUnitPrice() + "）");
        }
        BigDecimal weekendPrice = dto.getWeekendUnitPrice() != null ? dto.getWeekendUnitPrice() : dto.getUnitPrice();
        if (dto.getWeekendDailyCap() != null && dto.getWeekendDailyCap().compareTo(weekendPrice) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "周末封顶（" + dto.getWeekendDailyCap() + "）不能低于周末单位价格（" + weekendPrice + "）");
        }
        if (dto.getMonthlyPrice() != null && dto.getDailyCap() != null
                && dto.getMonthlyPrice().compareTo(dto.getDailyCap()) < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "月租价格低于日封顶不合理，请核对");
        }
    }

    private void apply(BillingRule rule, BillingRuleDTO dto) {
        rule.setRuleName(dto.getRuleName().trim());
        rule.setVehicleType(dto.getVehicleType());
        rule.setPlateColor(dto.getPlateColor() == null || dto.getPlateColor().isBlank() ? null : dto.getPlateColor().trim());
        rule.setFreeMinutes(dto.getFreeMinutes());
        rule.setUnitPrice(dto.getUnitPrice());
        rule.setUnitDuration(dto.getUnitDuration());
        rule.setDailyCap(dto.getDailyCap());
        rule.setMonthlyPrice(dto.getMonthlyPrice());
        rule.setWeekendUnitPrice(dto.getWeekendUnitPrice());
        rule.setWeekendDailyCap(dto.getWeekendDailyCap());
        rule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private void recordAudit(String userId, String action, BillingRule rule) {
        Long uid = null;
        if (userId != null && !userId.isBlank()) {
            try {
                uid = Long.valueOf(userId.trim());
            } catch (NumberFormatException ignored) {
                // 网关已保证 X-User-Id 是数字，异常时仅丢审计归属，不阻断业务
            }
        }
        String detail = "{\"id\":" + rule.getId() + ",\"ruleName\":\"" + rule.getRuleName()
                + "\",\"unitPrice\":" + rule.getUnitPrice() + ",\"unitDuration\":" + rule.getUnitDuration()
                + ",\"dailyCap\":" + rule.getDailyCap() + "}";
        auditClient.record(uid, null, action, "WRITE /api/order/billing-rules", detail, true);
    }
}
