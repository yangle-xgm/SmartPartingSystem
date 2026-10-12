package com.smartparking.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
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
            return Result.fail("设备不存在");
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
        deviceService.updateById(device);
        return Result.success();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.removeById(id);
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
}
