package com.smartparking.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.user.po.AuditLog;
import com.smartparking.user.query.AuditLogQuery;

/**
 * 操作审计日志服务
 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 记录操作日志
     *
     * @param userId    操作人ID
     * @param username  操作人用户名
     * @param operation 操作类型（如：登录/注册/入场/出场/支付）
     * @param method    方法全路径
     * @param params    参数JSON
     * @param success   是否成功
     * @param ip        IP
     * @param costTime  耗时毫秒
     */
    void record(Long userId, String username, String operation, String method,
                String params, boolean success, String ip, Long costTime);

    /**
     * 分页查询
     */
    Page<AuditLog> pageList(Page<AuditLog> page, AuditLogQuery query);
}
