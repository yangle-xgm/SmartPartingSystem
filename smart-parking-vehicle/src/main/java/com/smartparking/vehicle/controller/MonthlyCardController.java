package com.smartparking.vehicle.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.vehicle.po.MonthlyCardOrder;
import com.smartparking.vehicle.service.MonthlyCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 月卡充值控制器（业主端）。
 *
 * <p>替代原来「只有 admin 能点续费、且续费不收钱」的 {@code POST /api/vehicle/{id}/renew-monthly}，
 * 把充值拆成报价 → 下单 → 支付三步，业主可在 H5 自助办理。</p>
 */
@Tag(name = "月卡充值", description = "月卡套餐下单、模拟支付与到期续期")
@RestController
@RequestMapping("/api/vehicle/monthly")
@RequiredArgsConstructor
public class MonthlyCardController {

    private final MonthlyCardService monthlyCardService;

    @Operation(summary = "月卡试算（选月数实时报价）")
    @GetMapping("/quote")
    public Result<Map<String, Object>> quote(
            @Parameter(description = "车辆ID") @RequestParam Long vehicleId,
            @Parameter(description = "购买月数（1/3/6/12）") @RequestParam Integer months,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        return Result.success(monthlyCardService.quote(vehicleId, months,
                parseLong(currentUserId), currentRole));
    }

    @Operation(summary = "创建月卡订单（待支付）")
    @PostMapping("/create")
    public Result<MonthlyCardOrder> create(
            @Parameter(description = "车辆ID") @RequestParam Long vehicleId,
            @Parameter(description = "购买月数（1/3/6/12）") @RequestParam Integer months,
            @Parameter(description = "支付方式（1微信 2支付宝）") @RequestParam(defaultValue = "1") Integer payType,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        MonthlyCardOrder order = monthlyCardService.createOrder(vehicleId, months, payType,
                parseLong(currentUserId), currentRole);
        // 下单响应里的车牌是密文，前端不需要展示，置空避免误解
        order.setPlateNo(null);
        return Result.success(order);
    }

    @Operation(summary = "我的月卡订单")
    @GetMapping("/orders")
    public Result<List<MonthlyCardOrder>> orders(
            @Parameter(description = "业主ID（业主端取登录身份）") @RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        Long self = parseLong(currentUserId);
        Long target = "0".equals(currentRole) ? self : (userId != null ? userId : self);
        if (target == null) {
            throw new com.smartparking.common.core.exception.BizException(
                    ErrorCode.PARAM_INVALID, "缺少用户信息");
        }
        return Result.success(monthlyCardService.myOrders(target));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
