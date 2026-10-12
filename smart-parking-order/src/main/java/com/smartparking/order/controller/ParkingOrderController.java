package com.smartparking.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.order.client.AuditClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.query.ParkingOrderQuery;
import com.smartparking.order.service.ParkingOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 停车订单控制器
 */
@Tag(name = "订单管理", description = "停车订单管理、计费计算")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class ParkingOrderController {

    private final ParkingOrderService parkingOrderService;
    private final AuditClient auditClient;
    private final VehicleClient vehicleClient;

    @Operation(summary = "车辆入场，创建订单")
    @PostMapping("/entry")
    public Result<ParkingOrder> entry(
            @Parameter(description = "车辆ID") @RequestParam Long vehicleId,
            @Parameter(description = "车牌号") @RequestParam String plateNo,
            @Parameter(description = "车位ID") @RequestParam Long spaceId,
            @Parameter(description = "车位号") @RequestParam String spaceNo,
            @Parameter(description = "车辆类型（0私家车 1访客车辆 2月租车，可选）")
            @RequestParam(required = false) Integer vehicleType,
            @Parameter(description = "车牌颜色（如绿，可选，用于新能源优惠计费）")
            @RequestParam(required = false) String plateColor) {
        ParkingOrder order = parkingOrderService.createEntryOrder(
                vehicleId, plateNo, spaceId, spaceNo, vehicleType, plateColor);
        return Result.success(order);
    }

    @Operation(summary = "车辆出场，完成订单")
    @PostMapping("/exit/{orderNo}")
    public Result<ParkingOrder> exit(@PathVariable String orderNo) {
        ParkingOrder order = parkingOrderService.completeOrder(orderNo);
        return Result.success(order);
    }

    @Operation(summary = "分页查询订单列表")
    @GetMapping("/list")
    public Result<PageResult<ParkingOrder>> list(ParkingOrderQuery query,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主（role=0）强制只看自己车辆的订单，忽略前端传入的 vehicleIds，杜绝越权查看
        if ("0".equals(currentRole) && currentUserId != null) {
            List<Long> ownerVehicleIds = vehicleClient.listVehicleIdsByOwner(Long.valueOf(currentUserId));
            query.setVehicleIds(ownerVehicleIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        Page<ParkingOrder> page = new Page<>(query.getCurrent(), query.getSize());
        Page<ParkingOrder> result = parkingOrderService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public Result<ParkingOrder> detail(@PathVariable String orderNo) {
        ParkingOrder order = parkingOrderService.lambdaQuery()
                .eq(ParkingOrder::getOrderNo, orderNo)
                .one();
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.success(order);
    }

    @Operation(summary = "获取当前停车中的订单")
    @GetMapping("/current")
    public Result<ParkingOrder> current(
            @Parameter(description = "车辆ID") @RequestParam Long vehicleId) {
        ParkingOrder order = parkingOrderService.getCurrentOrder(vehicleId);
        return Result.success(order);
    }

    @Operation(summary = "实时计算停车费用")
    @GetMapping("/calculate")
    public Result<BigDecimal> calculate(
            @Parameter(description = "订单编号") @RequestParam String orderNo) {
        BigDecimal amount = parkingOrderService.calculateAmount(orderNo);
        return Result.success(amount);
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancel(@PathVariable String orderNo) {
        parkingOrderService.cancelOrder(orderNo);
        return Result.success();
    }

    // ==================== 内部接口 ====================

    @Operation(summary = "支付回调确认（内部接口）")
    @PostMapping("/internal/confirm-payment")
    public Result<Void> confirmPayment(
            @Parameter(description = "订单ID") @RequestParam Long orderId,
            @Parameter(description = "支付金额") @RequestParam BigDecimal amount) {
        parkingOrderService.confirmPayment(orderId, amount);
        return Result.success();
    }

    @Operation(summary = "支付超时关闭订单（内部接口，支付补偿定时任务调用）")
    @PostMapping("/internal/payment-timeout")
    public Result<Void> paymentTimeout(
            @Parameter(description = "订单ID") @RequestParam Long orderId) {
        parkingOrderService.timeoutClose(orderId);
        return Result.success();
    }

    @Operation(summary = "退款回调（内部接口，支付服务退款成功后调用）")
    @PostMapping("/internal/refund-callback")
    public Result<Void> refundCallback(@Parameter(description = "订单ID") @RequestParam Long orderId) {
        parkingOrderService.refundOrder(orderId);
        return Result.success();
    }

    @Operation(summary = "模糊车牌人工确认（业主从 suggestList 选择正确车牌后入场）")
    @PostMapping("/internal/confirm-plate")
    public Result<Map<String, Object>> confirmPlate(
            @RequestParam String plateNo,
            @RequestParam(required = false, defaultValue = "HUMAN") String deviceNo) {
        return Result.success(parkingOrderService.deviceEntry(plateNo, 100, deviceNo));
    }

    @Operation(summary = "查询订单信息（内部接口，支付服务校验与分润使用）")
    @GetMapping("/internal/order-info")
    public Result<Map<String, Object>> orderInfo(@Parameter(description = "订单ID") @RequestParam Long orderId) {
        return Result.success(parkingOrderService.getOrderInfo(orderId));
    }

    @Operation(summary = "设备联动入场（内部接口，MQTT摄像头识别后调用）")
    @PostMapping("/internal/device-entry")
    public Result<Map<String, Object>> deviceEntry(
            @Parameter(description = "车牌号") @RequestParam String plateNo,
            @Parameter(description = "置信度") @RequestParam(required = false, defaultValue = "100") Integer confidence,
            @Parameter(description = "设备编号") @RequestParam(required = false) String deviceNo) {
        Map<String, Object> result = parkingOrderService.deviceEntry(plateNo, confidence, deviceNo);
        return Result.success(result);
    }

    @Operation(summary = "进行中订单数（内部接口，统计服务使用）")
    @GetMapping("/internal/ongoing-count")
    public Result<Long> ongoingCount() {
        return Result.success(parkingOrderService.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.smartparking.order.po.ParkingOrder>()
                .eq(com.smartparking.order.po.ParkingOrder::getStatus, com.smartparking.common.core.constant.Constants.ORDER_STATUS_ONGOING)));
    }

    @Operation(summary = "设备联动出场（内部接口，出口摄像头识别后调用）")
    @PostMapping("/internal/device-exit")
    public Result<Map<String, Object>> deviceExit(
            @Parameter(description = "车牌号") @RequestParam String plateNo,
            @Parameter(description = "设备编号") @RequestParam(required = false) String deviceNo) {
        return Result.success(parkingOrderService.deviceExit(plateNo, deviceNo));
    }
}
