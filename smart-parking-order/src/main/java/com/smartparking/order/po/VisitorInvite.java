package com.smartparking.order.po;

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
 * 访客邀请码实体
 */
@Data
@TableName("visitor_invite")
@Schema(description = "访客邀请码")
public class VisitorInvite implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "邀请业主ID")
    private Long userId;

    @Schema(description = "访客车牌号（AES加密存储）")
    private String plateNo;

    @Schema(description = "邀请码（6位）")
    private String inviteCode;

    @Schema(description = "状态（0有效 1已使用 2已过期 3已作废）")
    private Integer status;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "使用时间")
    private LocalDateTime usedTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 车牌脱敏显示（非数据库字段） */
    @TableField(exist = false)
    private String plateNoMasked;
}
