package com.smartparking.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.user.po.AuditLog;
import com.smartparking.user.query.AuditLogQuery;
import com.smartparking.user.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 操作审计控制器
 */
@Tag(name = "操作审计", description = "操作日志查询与记录")
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询操作日志")
    @GetMapping("/list")
    public Result<PageResult<AuditLog>> list(AuditLogQuery query) {
        Page<AuditLog> page = new Page<>(query.getCurrent(), query.getSize());
        Page<AuditLog> result = auditLogService.pageList(page, query);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(),
                query.getCurrent(), query.getSize()));
    }

    @Operation(summary = "记录操作日志（内部接口，供各微服务调用）")
    @PostMapping("/internal/record")
    public Result<Void> record(
            @Parameter(description = "操作人ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "操作人用户名") @RequestParam(required = false) String username,
            @Parameter(description = "操作类型") @RequestParam String operation,
            @Parameter(description = "方法全路径") @RequestParam(required = false) String method,
            @Parameter(description = "参数JSON") @RequestParam(required = false) String params,
            @Parameter(description = "是否成功（0失败 1成功）") @RequestParam(required = false, defaultValue = "1") Integer isSuccess,
            @Parameter(description = "IP") @RequestParam(required = false) String ip,
            @Parameter(description = "耗时毫秒") @RequestParam(required = false) Long costTime) {
        auditLogService.record(userId, username, operation, method, params,
                isSuccess == null || isSuccess == 1, ip, costTime);
        return Result.success();
    }
}
