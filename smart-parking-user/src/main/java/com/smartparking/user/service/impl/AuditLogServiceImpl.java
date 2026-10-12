package com.smartparking.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.user.mapper.AuditLogMapper;
import com.smartparking.user.po.AuditLog;
import com.smartparking.user.query.AuditLogQuery;
import com.smartparking.user.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作审计日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void record(Long userId, String username, String operation, String method,
                       String params, boolean success, String ip, Long costTime) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setOperation(operation);
            auditLog.setMethod(method);
            // 参数过长截断，避免撑爆TEXT
            if (params != null && params.length() > 1000) {
                params = params.substring(0, 1000);
            }
            auditLog.setParams(params);
            auditLog.setIsSuccess(success ? 1 : 0);
            auditLog.setIp(ip);
            auditLog.setCostTime(costTime);
            this.save(auditLog);
        } catch (Exception e) {
            // 审计失败不影响主流程
            log.error("记录操作日志失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public Page<AuditLog> pageList(Page<AuditLog> page, AuditLogQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()), AuditLog::getUsername, query.getUsername())
                .eq(StringUtils.hasText(query.getOperation()), AuditLog::getOperation, query.getOperation())
                .eq(query.getIsSuccess() != null, AuditLog::getIsSuccess, query.getIsSuccess());
        // 注意：wrapper 的 .ge/.le 条件参数会被 Java 提前求值，
        // 必须先判空再 parse，否则 startTime/endTime 为 null 时 LocalDateTime.parse(null,...) 会抛 NPE
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AuditLog::getCreateTime,
                    LocalDateTime.parse(query.getStartTime(), TIME_FORMAT));
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AuditLog::getCreateTime,
                    LocalDateTime.parse(query.getEndTime(), TIME_FORMAT));
        }
        wrapper.orderByDesc(AuditLog::getCreateTime);
        return this.page(page, wrapper);
    }
}
