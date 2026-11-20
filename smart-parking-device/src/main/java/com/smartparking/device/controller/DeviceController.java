package com.smartparking.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.device.po.Device;
import com.smartparking.device.query.DeviceQuery;
import com.smartparking.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备控制器
 */
@Tag(name = "设备管理", description = "设备信息、状态监控、道闸控制")
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "设备列表")
    @GetMapping("/list")
    public Result<PageResult<Device>> list(DeviceQuery query) {
        Page<Device> page = new Page<>(query.getCurrent(), query.getSize());
        Page<Device> result = deviceService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "设备详情")
    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.fail(ErrorCode.DEVICE_NOT_FOUND);
        }
        return Result.success(device);
    }

    @Operation(summary = "注册设备")
    @PostMapping
    public Result<Device> register(@RequestBody Device device) {
        Device registered = deviceService.registerDevice(device);
        return Result.success(registered);
    }

    @Operation(summary = "更新设备信息")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        if (!deviceService.updateById(device)) {
            return Result.fail(ErrorCode.DEVICE_NOT_FOUND);
        }
        return Result.success();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!deviceService.removeById(id)) {
            return Result.fail(ErrorCode.DEVICE_NOT_FOUND);
        }
        return Result.success();
    }

    @Operation(summary = "手动开启道闸")
    @PostMapping("/gate/open")
    public Result<Void> openGate(
            @Parameter(description = "设备编号") @RequestParam String deviceNo) {
        deviceService.openGate(deviceNo);
        return Result.success();
    }

    @Operation(summary = "手动关闭道闸")
    @PostMapping("/gate/close")
    public Result<Void> closeGate(
            @Parameter(description = "设备编号") @RequestParam String deviceNo) {
        deviceService.closeGate(deviceNo);
        return Result.success();
    }

    @Operation(summary = "设备状态概览")
    @GetMapping("/status/overview")
    public Result<Map<String, Object>> statusOverview() {
        Map<String, Object> overview = deviceService.getStatusOverview();
        return Result.success(overview);
    }

    @Operation(summary = "模拟摄像头识别（MQTT联动：识别→自动入场→道闸开启）")
    @PostMapping("/mock/camera")
    public Result<Map<String, Object>> mockCamera(
            @Parameter(description = "摄像头设备编号") @RequestParam String deviceNo,
            @Parameter(description = "识别车牌号") @RequestParam String plateNo,
            @Parameter(description = "置信度（默认100，低于80走模糊兜底）") @RequestParam(required = false, defaultValue = "100") Integer confidence) {
        Map<String, Object> result = deviceService.mockCameraRecognize(deviceNo, plateNo, confidence);
        return Result.success(result);
    }

    @Operation(summary = "模拟出口摄像头识别（联动出场：识别→自动结算→道闸开启）")
    @PostMapping("/mock/exit-camera")
    public Result<Map<String, Object>> mockExitCamera(
            @Parameter(description = "出口摄像头设备编号") @RequestParam String deviceNo,
            @Parameter(description = "识别车牌号") @RequestParam String plateNo) {
        Map<String, Object> result = deviceService.mockExitRecognize(deviceNo, plateNo);
        return Result.success(result);
    }

    @Operation(summary = "手动出场放行（门岗操作：指定车牌直接结算出场）")
    @PostMapping("/gate/exit")
    public Result<Map<String, Object>> gateExit(
            @Parameter(description = "道闸设备编号") @RequestParam String deviceNo,
            @Parameter(description = "出场车牌号") @RequestParam String plateNo) {
        Map<String, Object> result = deviceService.manualExit(deviceNo, plateNo);
        return Result.success(result);
    }

    @Operation(summary = "道闸指令流水（沙箱/厂商通道下发记录，新的在前）")
    @GetMapping("/gate/command-log")
    public Result<java.util.List<Map<String, Object>>> commandLog(
            @Parameter(description = "返回条数（默认20，最多200）") @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(deviceService.recentGateCommands(limit));
    }

    // ==================== 内部接口 ====================

    @Operation(summary = "更新设备心跳（内部接口）")
    @PostMapping("/internal/heartbeat")
    public Result<Void> heartbeat(
            @Parameter(description = "设备编号") @RequestParam String deviceNo) {
        deviceService.updateHeartbeat(deviceNo);
        return Result.success();
    }

    @Operation(summary = "更新设备状态（内部接口）")
    @PutMapping("/internal/{deviceNo}/status")
    public Result<Void> updateStatus(
            @PathVariable String deviceNo,
            @Parameter(description = "状态") @RequestParam Integer status) {
        deviceService.updateStatus(deviceNo, status);
        return Result.success();
    }

    /**
     * 支付成功联动开闸：payment 服务在核销成功后调用，指令经厂商适配器下发。
     *
     * <p>走 {@code /internal/**} 而非管理端接口，是为了让 payment 服务用
     * {@code X-Internal-Token} 直连，不必伪造管理员 JWT（网关上 /internal/** 只认令牌）。
     *
     * <p>返回体用 {@code success} 字段表达"指令是否被通道受理"，HTTP 恒 200：
     * 开闸失败不该让支付服务的调用按 4xx/5xx 走重试逻辑（钱已经收了）。
     */
    @Operation(summary = "支付成功联动开闸（内部接口，由 payment 服务核销成功后调用）")
    @PostMapping("/internal/gate/release")
    public Result<Map<String, Object>> releaseForPayment(
            @Parameter(description = "支付流水号") @RequestParam(required = false) String payNo,
            @Parameter(description = "订单ID") @RequestParam(required = false) String orderId,
            @Parameter(description = "开闸原因（写入指令报文与日志）") @RequestParam(required = false) String reason,
            @Parameter(description = "指定道闸编号；不传则自动挑选在线出口道闸") @RequestParam(required = false) String deviceNo) {
        return Result.success(deviceService.releaseForPayment(payNo, orderId, reason, deviceNo));
    }
}
