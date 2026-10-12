package com.smartparking.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.user.po.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
