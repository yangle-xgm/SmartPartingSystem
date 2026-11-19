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
            @Parameter(description = "邀请业主ID（业主传值无效，服务端强制取登录人）") @RequestParam(required = false) Long userId,
            @Parameter(description = "访客车牌号") @RequestParam String plateNo,
            @Parameter(description = "有效期（小时，默认24）") @RequestParam(required = false, defaultValue = "24") Integer expireHours,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 报告 P1-6：原实现信任 body/param 的 userId，可替他人生成邀请并让费用挂到别人账上
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        if (userId == null) {
            userId = currentUserId == null ? null : Long.valueOf(currentUserId);
        }
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

    @Operation(summary = "访客车牌自动识别入场（门岗/设备专用，仅管理端）")
    @PostMapping("/entry-by-plate")
    public Result<ParkingOrder> entryByPlate(
            @Parameter(description = "访客车牌号") @RequestParam String plateNo,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 报告 P1-6：该接口会真实放行车辆，任何登录用户都能调等于"任意车牌免费入场"。
        // 访客自助入场请走 /entry（凭邀请码），设备侧走 /api/order/internal/device-entry。
        if (!"1".equals(currentRole) && !"2".equals(currentRole)) {
            return Result.fail(com.smartparking.common.core.exception.ErrorCode.FORBIDDEN,
                    "仅门岗/管理端可使用车牌放行，访客请凭邀请码入场");
        }
        ParkingOrder order = visitorInviteService.entryByPlateNo(plateNo);
        return Result.success(order);
    }

    @Operation(summary = "作废邀请码")
    @PostMapping("/invalidate")
    public Result<Void> invalidate(
            @Parameter(description = "邀请ID") @RequestParam Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        // 归属判断用网关注入的登录人，不再信任请求参数（否则传别人的 userId 即可作废他人邀请）
        visitorInviteService.invalidate(id, currentUserId == null ? null : Long.valueOf(currentUserId));
        return Result.success();
    }
}
