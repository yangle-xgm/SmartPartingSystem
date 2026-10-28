package com.smartparking.notify.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.notify.po.Notification;
import com.smartparking.notify.query.NotificationQuery;
import com.smartparking.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器
 */
@Slf4j
@Tag(name = "通知管理", description = "消息通知管理")
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 网关地址（广播时拉取业主列表，走 /api/user/internal/owner-ids） */
    @Value("${smart-parking.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Operation(summary = "发送通知")
    @PostMapping("/send")
    public Result<Void> send(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "标题") @RequestParam String title,
            @Parameter(description = "内容") @RequestParam String content,
            @Parameter(description = "类型（1入场 2出场 3缴费提醒 4系统公告）") @RequestParam Integer type) {
        notificationService.sendNotification(userId, title, content, type);
        return Result.success();
    }

    @Operation(summary = "管理员发送通知（支持全体广播）")
    @PostMapping("/admin/send")
    public Result<Void> adminSend(@RequestBody java.util.Map<String, Object> body) {
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        Integer type = (Integer) body.get("type");
        Integer target = (Integer) body.get("target"); // 0=全体 1=指定
        if (target != null && target == 0) {
            // 全体广播：为每位业主生成独立通知，已读状态互不影响
            List<Long> ownerIds = fetchOwnerIds();
            for (Long ownerId : ownerIds) {
                notificationService.sendNotification(ownerId, title, content, type);
            }
            // 管理端自己也留一条（发送方视角）
            notificationService.sendNotification(1L, title, content, type);
            return Result.success();
        } else {
            Long userId = Long.valueOf(String.valueOf(body.get("userId")));
            notificationService.sendNotification(userId, title, content, type);
            return Result.success();
        }
    }

    /**
     * 从用户服务获取全部业主ID（角色=0）
     */
    @SuppressWarnings("unchecked")
    private List<Long> fetchOwnerIds() {
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    gatewayUrl + "/api/user/internal/owner-ids", Map.class);
            if (resp == null || resp.get("data") == null) {
                return List.of();
            }
            Object data = resp.get("data");
            if (data instanceof List<?> list) {
                return list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("拉取业主列表失败，广播降级为单条：{}", e.getMessage());
            return List.of();
        }
    }

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public Result<PageResult<Notification>> list(NotificationQuery query,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // E2E 报告 P1-6/P2-6：任何角色都只返回"发给本人的 + 全体广播(userId=1)"。
        // 此前仅业主(role=0)强制覆盖 userId，管理员不传 userId 时返回全站通知，
        // 既把"我的通知"当成"通知管理"用，又泄露他人停车信息。
        if (currentUserId != null) {
            query.setUserId(Long.valueOf(currentUserId));
        }
        Page<Notification> page = new Page<>(query.getCurrent(), query.getSize());
        Page<Notification> result = notificationService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // E2E 报告 P1-6：业主只能已读自己的通知（广播通知 userId=1 对所有业主可见可读）
        if ("0".equals(currentRole) && currentUserId != null) {
            Notification n = notificationService.getById(id);
            if (n != null && !Long.valueOf(currentUserId).equals(n.getUserId()) && !Long.valueOf(1L).equals(n.getUserId())) {
                throw new BizException(ErrorCode.NOTIFICATION_NOT_FOUND, "无权操作该通知");
            }
        }
        notificationService.markAsRead(id);
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/readAll")
    public Result<Void> markAllAsRead(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // E2E 报告 P1-6：业主(role=0)忽略入参 userId，强制只已读本人的通知
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread/count")
    public Result<Long> unreadCount(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // E2E 报告 P1-6：业主(role=0)忽略入参 userId，强制只统计本人的未读
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        long count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }

    // ==================== 内部接口 ====================

    @Operation(summary = "发送入场通知（内部接口）")
    @PostMapping("/internal/entry")
    public Result<Void> sendEntryNotification(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "车牌号") @RequestParam String plateNo,
            @Parameter(description = "车位号") @RequestParam String spaceNo) {
        String title = "车辆入场通知";
        String content = String.format("您的车辆 %s 已于 %s 入场，停放在 %s",
                plateNo, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), spaceNo);
        notificationService.sendNotification(userId, title, content, 1);
        return Result.success();
    }

    @Operation(summary = "发送出场通知（内部接口）")
    @PostMapping("/internal/exit")
    public Result<Void> sendExitNotification(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "车牌号") @RequestParam String plateNo,
            @Parameter(description = "停车时长") @RequestParam Integer duration,
            @Parameter(description = "费用") @RequestParam String amount) {
        String title = "车辆出场通知";
        String content = String.format("您的车辆 %s 已出场，停车时长 %d 分钟，费用 %s 元",
                plateNo, duration, amount);
        notificationService.sendNotification(userId, title, content, 2);
        return Result.success();
    }

    @Operation(summary = "发送缴费提醒（内部接口）")
    @PostMapping("/internal/payment")
    public Result<Void> sendPaymentNotification(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "订单编号") @RequestParam String orderNo,
            @Parameter(description = "金额") @RequestParam String amount) {
        String title = "缴费提醒";
        String content = String.format("订单 %s 需支付 %s 元，请尽快完成支付", orderNo, amount);
        notificationService.sendNotification(userId, title, content, 3);
        return Result.success();
    }
}
