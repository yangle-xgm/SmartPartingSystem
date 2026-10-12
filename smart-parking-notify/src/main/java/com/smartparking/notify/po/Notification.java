package com.smartparking.notify.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
@Schema(description = "通知")
public class Notification extends BaseEntity {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "类型（1入场通知 2出场通知 3缴费提醒 4系统公告）")
    private Integer type;

    @Schema(description = "是否已读（0未读 1已读）")
    private Integer isRead;
}
