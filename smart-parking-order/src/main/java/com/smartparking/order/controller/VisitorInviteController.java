package com.smartparking.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.po.VisitorInvite;
import com.smartparking.order.query.VisitorInviteQuery;
import com.smartparking.order.service.VisitorInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 访客邀请码控制器（访客管理模块）
 */
@Tag(name = "访客管理", description = "访客邀请码生成、凭码入场")
@RestController
@RequestMapping("/api/order/invite")
@RequiredArgsConstructor
public class VisitorInviteController {

    private final VisitorInviteService visitorInviteService;

    @Operation(summary = "业主生成访客邀请码")
    @PostMapping("/create")
    public Result<VisitorInvite> create(
            @Parameter(description = "邀请业主ID") @RequestParam Long userId,
            @Parameter(description = "访客车牌号") @RequestParam String plateNo,
            @Parameter(description = "有效期（小时，默认24）") @RequestParam(required = false, defaultValue = "24") Integer expireHours) {
        VisitorInvite invite = visitorInviteService.createInvite(userId, plateNo, expireHours);
        return Result.success(invite);
    }

    @Operation(summary = "我的访客邀请列表")
    @GetMapping("/list")
    public Result<PageResult<VisitorInvite>> list(VisitorInviteQuery query,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主（role=0）只能看自己的访客邀请，忽略前端传入的 userId
        if ("0".equals(currentRole) && currentUserId != null) {
            query.setUserId(Long.valueOf(currentUserId));
        }
        Page<VisitorInvite> page = new Page<>(query.getCurrent(), query.getSize());
        Page<VisitorInvite> result = visitorInviteService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "访客凭码入场（模拟道闸/门岗核验）")
    @PostMapping("/entry")
    public Result<ParkingOrder> entry(
            @Parameter(description = "邀请码") @RequestParam String inviteCode) {
        ParkingOrder order = visitorInviteService.entryByInviteCode(inviteCode);
        return Result.success(order);
    }

    @Operation(summary = "访客车牌自动识别入场（白名单，供门岗/设备人工核验）")
    @PostMapping("/entry-by-plate")
    public Result<ParkingOrder> entryByPlate(
            @Parameter(description = "访客车牌号") @RequestParam String plateNo) {
        ParkingOrder order = visitorInviteService.entryByPlateNo(plateNo);
        return Result.success(order);
    }

    @Operation(summary = "作废邀请码")
    @PostMapping("/invalidate")
    public Result<Void> invalidate(
            @Parameter(description = "邀请ID") @RequestParam Long id,
            @Parameter(description = "操作人ID") @RequestParam Long userId) {
        visitorInviteService.invalidate(id, userId);
        return Result.success();
    }
}
