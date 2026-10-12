package com.smartparking.vehicle.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.vehicle.dto.RecognizeResult;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车辆控制器
 */
@Tag(name = "车辆管理", description = "车辆绑定、查询、车牌识别")
@RestController
@RequestMapping("/api/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "车辆列表（传userId查个人车辆，不传查全部-管理端）")
    @GetMapping("/list")
    public Result<List<Vehicle>> list(
            @Parameter(description = "用户ID（可选，不传返回全部车辆）")
            @RequestParam(required = false) Long userId) {
        List<Vehicle> vehicles = vehicleService.listByUserId(userId);
        return Result.success(vehicles);
    }

    @Operation(summary = "绑定车辆")
    @PostMapping("/bind")
    public Result<Vehicle> bind(@RequestBody Vehicle vehicle) {
        Vehicle bound = vehicleService.bindVehicle(vehicle);
        return Result.success(bound);
    }

    @Operation(summary = "解绑车辆")
    @DeleteMapping("/unbind/{id}")
    public Result<Void> unbind(
            @PathVariable Long id,
            @Parameter(description = "用户ID") @RequestParam Long userId) {
        vehicleService.unbindVehicle(id, userId);
        return Result.success();
    }

    @Operation(summary = "车牌识别（模拟，含模糊识别兜底）")
    @PostMapping("/recognize")
    public Result<RecognizeResult> recognize(
            @Parameter(description = "识别到的车牌号") @RequestParam String plateNo,
            @Parameter(description = "识别置信度（0-100，低于80进入模糊识别兜底）")
            @RequestParam(required = false, defaultValue = "100") Integer confidence) {
        return Result.success(vehicleService.recognize(plateNo, confidence));
    }

    @Operation(summary = "根据车牌查询车辆信息（内部接口）")
    @GetMapping("/internal/info/{plateNo}")
    public Result<Vehicle> getInfoByPlate(@PathVariable String plateNo) {
        Vehicle vehicle = vehicleService.getByPlateNo(plateNo);
        if (vehicle == null) {
            return Result.fail("车辆未注册");
        }
        return Result.success(vehicle);
    }

    @Operation(summary = "按业主查询车辆ID列表（内部接口，订单数据隔离使用）")
    @GetMapping("/internal/list-by-owner")
    public Result<java.util.List<Long>> listIdsByOwner(@RequestParam Long ownerId) {
        java.util.List<Vehicle> vehicles = vehicleService.listByUserId(ownerId);
        return Result.success(vehicles.stream().map(Vehicle::getId).toList());
    }

    @Operation(summary = "更新车辆状态（内部接口）")
    @PutMapping("/internal/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        vehicleService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "加入/移出黑名单")
    @PutMapping("/{id}/blacklist")
    public Result<Void> toggleBlacklist(@PathVariable Long id, @RequestParam Integer status) {
        vehicleService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "月卡车续费（续一个月）")
    @PostMapping("/{id}/renew-monthly")
    public Result<Void> renewMonthly(@PathVariable Long id) {
        vehicleService.renewMonthly(id);
        return Result.success();
    }
}
