package com.smartparking.user.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作审计日志查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作审计日志查询条件")
public class AuditLogQuery extends PageQuery {

    @Schema(description = "操作人用户名（模糊）")
    private String username;

    @Schema(description = "操作类型（精确）")
    private String operation;

    @Schema(description = "操作结果（0失败 1成功）")
    private Integer isSuccess;

    @Schema(description = "开始时间 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "结束时间 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}
