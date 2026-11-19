package com.smartparking.vehicle.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.ErrorCode;
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

    @Operation(summary = "车辆列表（业主只能查本人车辆，管理端可查全部）")
    @GetMapping("/list")
    public Result<List<Vehicle>> list(
            @Parameter(description = "用户ID（管理端筛选用；业主传值无效，服务端强制取登录人）")
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主(role=0)只能看自己的车辆：忽略前端传入的 userId，防遍历他人车辆（报告 P1-6）
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        List<Vehicle> vehicles = vehicleService.listByUserId(userId);
        return Result.success(vehicles);
    }

    @Operation(summary = "绑定车辆（业主只能给自己绑定，月卡字段由服务端固定）")
    @PostMapping("/bind")
    public Result<Vehicle> bind(@RequestBody Vehicle vehicle,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        Vehicle bound = vehicleService.bindVehicle(vehicle, currentUserId, currentRole);
        return Result.success(bound);
    }

    @Operation(summary = "解绑车辆（归属以网关透传的登录人为准）")
    @DeleteMapping("/unbind/{id}")
    public Result<Void> unbind(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        // 旧实现用 @RequestParam userId 做归属比对，A 传 B 的 userId 即可解绑他人车辆（报告 P0-5）。
        // 现改为只认网关注入的 X-User-Id，前端无需再传 userId。
        vehicleService.unbindVehicle(id, currentUserId == null ? null : Long.valueOf(currentUserId));
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
            return Result.fail(ErrorCode.VEHICLE_NOT_FOUND);
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

    @Operation(summary = "加入/移出黑名单（管理端）")
    @PutMapping("/{id}/blacklist")
    public Result<Void> toggleBlacklist(@PathVariable Long id,
            @RequestParam Integer status,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 网关注入的角色兜底校验：黑名单直接影响道闸放行，不能由业主自助操作
        if (!isAdminRole(currentRole)) {
            return Result.fail(ErrorCode.FORBIDDEN, "仅管理端可操作车辆黑名单");
        }
        vehicleService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "月卡车续费一个月（管理端人工补录；业主端走 报价→下单→支付）")
    @PostMapping("/{id}/renew-monthly")
    public Result<Void> renewMonthly(@PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 报告 P0-3：该接口原本无任何鉴权，业主调用即可免费延长月卡
        if (!isAdminRole(currentRole)) {
            return Result.fail(ErrorCode.FORBIDDEN, "月卡续费需走支付流程，业主不可直接调用");
        }
        vehicleService.renewMonthly(id);
        return Result.success();
    }

    private boolean isAdminRole(String role) {
        return "1".equals(role) || "2".equals(role);
    }
}
