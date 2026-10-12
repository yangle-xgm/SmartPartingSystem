package com.smartparking.user.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志实体
 *
 * 注意：audit_log 表无 update_time / is_deleted 字段，故不继承 BaseEntity。
 */
@Data
@TableName("audit_log")
@Schema(description = "操作审计日志")
public class AuditLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "操作人ID")
    private Long userId;

    @Schema(description = "操作人用户名")
    private String username;

    @Schema(description = "操作类型")
    private String operation;

    @Schema(description = "请求方法全路径")
    private String method;

    @Schema(description = "请求参数（JSON）")
    private String params;

    @Schema(description = "操作结果（0失败 1成功）")
    private Integer isSuccess;

    @Schema(description = "操作IP地址")
    private String ip;

    @Schema(description = "耗时（毫秒）")
    private Long costTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
