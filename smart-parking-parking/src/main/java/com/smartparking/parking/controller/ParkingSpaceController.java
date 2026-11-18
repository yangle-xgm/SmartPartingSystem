package com.smartparking.parking.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.query.ParkingSpaceQuery;
import com.smartparking.parking.service.ParkingSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 车位控制器
 */
@Tag(name = "车位管理", description = "车位信息管理、状态查询、预约")
@RestController
@RequestMapping("/api/parking")
@RequiredArgsConstructor
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;

    @Operation(summary = "分页查询车位列表")
    @GetMapping("/spaces")
    public Result<PageResult<ParkingSpace>> pageList(ParkingSpaceQuery query,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole,
            @RequestHeader(value = "X-Community-Id", required = false) String communityId) {
        // 超管（role=2）看全部小区；业主/物业按所属小区过滤
        Long community = resolveCommunityId(currentRole, communityId);
        Page<ParkingSpace> page = new Page<>(query.getCurrent(), query.getSize());
        Page<ParkingSpace> result = parkingSpaceService.pageList(page, query, community);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "车位详情")
    @GetMapping("/space/{id}")
    public Result<ParkingSpace> getDetail(@PathVariable Long id) {
        ParkingSpace space = parkingSpaceService.getById(id);
        if (space == null) {
            return Result.fail(ErrorCode.SPACE_NOT_FOUND);
        }
        return Result.success(space);
    }

    @Operation(summary = "新增车位")
    @PostMapping("/space")
    public Result<Void> add(@Valid @RequestBody ParkingSpace space) {
        space.setStatus(0); // 默认空闲
        parkingSpaceService.save(space);
        return Result.success();
    }

    @Operation(summary = "更新车位信息")
    @PutMapping("/space/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ParkingSpace space) {
        space.setId(id);
        // DEF-01：影响 0 行说明 id 不存在（含雪花 id 精度丢失的错 id），不能再返回 200 假成功
        if (!parkingSpaceService.updateById(space)) {
            return Result.fail(ErrorCode.SPACE_NOT_FOUND);
        }
        return Result.success();
    }

    @Operation(summary = "删除车位")
    @DeleteMapping("/space/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!parkingSpaceService.removeById(id)) {
            return Result.fail(ErrorCode.SPACE_NOT_FOUND);
        }
        return Result.success();
    }

    @Operation(summary = "查询可用车位")
    @GetMapping("/available")
    public Result<List<ParkingSpace>> available(
            @Parameter(description = "区域") @RequestParam(required = false) String area,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole,
            @RequestHeader(value = "X-Community-Id", required = false) String communityId) {
        Long community = resolveCommunityId(currentRole, communityId);
        List<ParkingSpace> spaces = parkingSpaceService.findAvailableSpaces(area, community);
        return Result.success(spaces);
    }

    @Operation(summary = "查询全部车位（实时地图，含占用/预约/维护）")
    @GetMapping("/all")
    public Result<List<ParkingSpace>> all(
            @Parameter(description = "区域") @RequestParam(required = false) String area,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole,
            @RequestHeader(value = "X-Community-Id", required = false) String communityId) {
        Long community = resolveCommunityId(currentRole, communityId);
        List<ParkingSpace> spaces = parkingSpaceService.findAllSpaces(area, community);
        return Result.success(spaces);
    }

    @Operation(summary = "车位状态统计")
    @GetMapping("/status/statistics")
    public Result<Map<String, Object>> statusStatistics(
            @RequestHeader(value = "X-User-Role", required = false) String currentRole,
            @RequestHeader(value = "X-Community-Id", required = false) String communityId) {
        Long community = resolveCommunityId(currentRole, communityId);
        Map<String, Object> stats = parkingSpaceService.getAreaStatusStatistics(community);
        return Result.success(stats);
    }

    /**
     * 解析小区ID：超管看全部（返回null），其他角色按透传小区过滤
     */
    private Long resolveCommunityId(String currentRole, String communityId) {
        if ("2".equals(currentRole)) {
            return null;
        }
        if (communityId != null && !communityId.isBlank()) {
            try {
                return Long.valueOf(communityId);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Operation(summary = "预约车位")
    @PostMapping("/reserve")
    public Result<Void> reserve(
            @Parameter(description = "车位ID") @RequestParam Long spaceId,
            @Parameter(description = "用户ID（业主端忽略此值，以网关注入的身份为准）") @RequestParam(required = false) Long userId,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 越权修复：业主(role=0)只能给自己预约，忽略前端传入的 userId
        Long effectiveUserId = userId;
        if ("0".equals(currentRole) || (currentRole == null && currentUserId != null)) {
            Long self = parseLong(currentUserId);
            if (self != null) effectiveUserId = self;
        }
        if (effectiveUserId == null) {
            return Result.fail(ErrorCode.PARAM_INVALID, "缺少预约人信息");
        }
        parkingSpaceService.reserveSpace(spaceId, effectiveUserId, startTime, endTime);
        return Result.success();
    }

    @Operation(summary = "取消预约")
    @PostMapping("/reserve/cancel")
    public Result<Void> cancelReserve(@RequestParam Long reservationId,
            @RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        Long effectiveUserId = "0".equals(currentRole) ? parseLong(currentUserId) : userId;
        if (effectiveUserId == null) {
            return Result.fail(ErrorCode.PARAM_INVALID, "缺少用户信息");
        }
        parkingSpaceService.cancelReservation(reservationId, effectiveUserId);
        return Result.success();
    }

    @Operation(summary = "我的预约列表")
    @GetMapping("/reserve/my")
    public Result<Object> myReservations(@RequestParam(required = false) Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        Long effectiveUserId = "0".equals(currentRole) ? parseLong(currentUserId) : userId;
        if (effectiveUserId == null) {
            return Result.fail(ErrorCode.PARAM_INVALID, "缺少用户信息");
        }
        return Result.success(parkingSpaceService.myReservations(effectiveUserId));
    }

    // ==================== 内部接口（供其他服务调用） ====================

    @Operation(summary = "更新车位状态（内部接口）")
    @PutMapping("/internal/space/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        parkingSpaceService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "原子占用车位（内部接口，入场防并发）")
    @PostMapping("/internal/space/{id}/claim")
    public Result<Boolean> claim(@PathVariable Long id) {
        return Result.success(parkingSpaceService.claimSpace(id));
    }

    @Operation(summary = "业主切换车位共享状态")
    @PutMapping("/space/{id}/share")
    public Result<Void> toggleShare(@PathVariable Long id,
            @RequestParam Integer isShared,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        Long userId = parseLong(currentUserId);
        parkingSpaceService.toggleShare(id, isShared, userId, currentRole);
        return Result.success();
    }

    @Operation(summary = "我的车位（业主端，按产权人查询固定车位）")
    @GetMapping("/space/my")
    public Result<List<ParkingSpace>> mySpaces(
            @Parameter(description = "业主ID") @RequestParam(required = false) Long ownerId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主只能看自己的车位；物业/超管可按 ownerId 代查
        Long target = "0".equals(currentRole) ? parseLong(currentUserId) : (ownerId != null ? ownerId : parseLong(currentUserId));
        return Result.success(parkingSpaceService.listMySpaces(target));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Operation(summary = "车位占用汇总（内部接口，统计服务回填占用率使用）")
    @GetMapping("/internal/occupancy-summary")
    public Result<Map<String, Object>> occupancySummary(
            @RequestHeader(value = "X-User-Role", required = false) String currentRole,
            @RequestHeader(value = "X-Community-Id", required = false) String communityId) {
        Long community = resolveCommunityId(currentRole, communityId);
        return Result.success(parkingSpaceService.getOccupancySummary(community));
    }

    @Operation(summary = "查询车位共享信息（内部接口，支付分润使用）")
    @GetMapping("/internal/space-info/{id}")
    public Result<Map<String, Object>> spaceInfo(@PathVariable Long id) {
        ParkingSpace space = parkingSpaceService.getById(id);
        if (space == null) {
            return Result.fail(ErrorCode.SPACE_NOT_FOUND);
        }
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", space.getId());
        info.put("spaceNo", space.getSpaceNo());
        info.put("ownerId", space.getOwnerId());
        info.put("isShared", space.getIsShared() != null && space.getIsShared() == 1);
        return Result.success(info);    }
}
