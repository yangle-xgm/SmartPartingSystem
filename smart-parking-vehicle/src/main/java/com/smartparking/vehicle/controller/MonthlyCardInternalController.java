package com.smartparking.vehicle.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.vehicle.po.MonthlyCardOrder;
import com.smartparking.vehicle.service.MonthlyCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 月卡内部接口（仅服务间调用，网关 + {@code InternalApiGuardFilter} 双重校验）。
 *
 * <p>归属说明：月卡订单表 {@code monthly_card_order} 属于车辆域，但「钱」由支付服务统一记账。
 * 因此这里只暴露两个支付服务需要的动作：核销前查订单、核销后续期。
 *
 * <p>为什么单独一个控制器：{@link MonthlyCardController} 的类级前缀是
 * {@code /api/vehicle/monthly}，内部接口的真实路径是 {@code /api/vehicle/internal/monthly/**}，
 * 两者前缀不同，混在一个类里会拼出 {@code /monthly/internal/monthly} 的畸形路径。
 */
@Tag(name = "月卡内部接口", description = "支付服务核销月卡流水时使用")
@RestController
@RequestMapping("/api/vehicle/internal/monthly")
@RequiredArgsConstructor
public class MonthlyCardInternalController {

    private final MonthlyCardService monthlyCardService;

    @Operation(summary = "月卡订单信息（支付服务创建流水前校验金额/状态/归属）")
    @GetMapping("/info")
    public Result<MonthlyCardOrder> info(
            @Parameter(description = "月卡订单ID") @RequestParam Long monthlyOrderId) {
        return Result.success(monthlyCardService.getMonthlyOrder(monthlyOrderId));
    }

    @Operation(summary = "支付到账后置为已支付并续期（幂等）")
    @PostMapping("/mark-paid")
    public Result<Map<String, Object>> markPaid(
            @Parameter(description = "月卡订单ID") @RequestParam Long monthlyOrderId,
            @Parameter(description = "支付流水号") @RequestParam(required = false) String payNo,
            @Parameter(description = "第三方交易号") @RequestParam(required = false) String transactionId) {
        return Result.success(monthlyCardService.markPaid(monthlyOrderId, payNo, transactionId));
    }
}
