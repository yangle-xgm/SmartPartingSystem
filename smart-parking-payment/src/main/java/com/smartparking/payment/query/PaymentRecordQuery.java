package com.smartparking.payment.query;

import com.smartparking.common.core.domain.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支付记录查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "支付记录查询条件")
public class PaymentRecordQuery extends PageQuery {

    @Schema(description = "支付用户ID")
    private Long userId;

    @Schema(description = "支付状态（0待支付 1成功 2失败 3退款）")
    private Integer payStatus;
}
