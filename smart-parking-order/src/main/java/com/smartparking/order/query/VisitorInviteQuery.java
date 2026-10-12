package com.smartparking.order.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 访客邀请码查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "访客邀请码查询条件")
public class VisitorInviteQuery extends PageQuery {

    @Schema(description = "邀请业主ID")
    private Long userId;

    @Schema(description = "状态（0有效 1已使用 2已过期 3已作废）")
    private Integer status;
}
