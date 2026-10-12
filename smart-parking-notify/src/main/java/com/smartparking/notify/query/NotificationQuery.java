package com.smartparking.notify.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知查询条件")
public class NotificationQuery extends PageQuery {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "类型（1入场通知 2出场通知 3缴费提醒 4系统公告）")
    private Integer type;
}
