package com.smartparking.parking.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.query.ParkingSpaceQuery;
import com.smartparking.parking.service.ParkingSpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            return Result.fail("车位不存在");
        }
        return Result.success(space);
    }

    @Operation(summary = "新增车位")
    @PostMapping("/space")
    public Result<Void> add(@RequestBody ParkingSpace space) {
        space.setStatus(0); // 默认空闲
        parkingSpaceService.save(space);
        return Result.success();
    }

    @Operation(summary = "更新车位信息")
    @PutMapping("/space/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ParkingSpace space) {
        space.setId(id);
        parkingSpaceService.updateById(space);
        return Result.success();
    }

    @Operation(summary = "删除车位")
    @DeleteMapping("/space/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        parkingSpaceService.removeById(id);
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
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        parkingSpaceService.reserveSpace(spaceId, userId, startTime, endTime);
        return Result.success();
    }

    @Operation(summary = "取消预约")
    @PostMapping("/reserve/cancel")
    public Result<Void> cancelReserve(@RequestParam Long reservationId, @RequestParam Long userId) {
        parkingSpaceService.cancelReservation(reservationId, userId);
        return Result.success();
    }

    @Operation(summary = "我的预约列表")
    @GetMapping("/reserve/my")
    public Result<Object> myReservations(@RequestParam Long userId) {
        return Result.success(parkingSpaceService.myReservations(userId));
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
    public Result<Void> toggleShare(@PathVariable Long id, @RequestParam Integer isShared) {
        parkingSpaceService.toggleShare(id, isShared);
        return Result.success();
    }

    @Operation(summary = "查询车位共享信息（内部接口，支付分润使用）")
    @GetMapping("/internal/space-info/{id}")
    public Result<Map<String, Object>> spaceInfo(@PathVariable Long id) {
        ParkingSpace space = parkingSpaceService.getById(id);
        if (space == null) {
            return Result.fail("车位不存在");
        }
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", space.getId());
        info.put("spaceNo", space.getSpaceNo());
        info.put("ownerId", space.getOwnerId());
        info.put("isShared", space.getIsShared() != null && space.getIsShared() == 1);
        return Result.success(info);
    }
}
