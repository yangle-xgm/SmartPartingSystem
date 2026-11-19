package com.smartparking.payment.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子发票实体。
 *
 * <p>状态机：0 待开票 → 1 已开票 → 2 已红冲；一单一票由 {@code uk_order_id} 兜底。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("invoice")
@Schema(description = "电子发票")
public class Invoice extends BaseEntity {

    @Schema(description = "发票号（开票时生成）")
    private String invoiceNo;

    @Schema(description = "停车订单ID")
    private Long orderId;

    @Schema(description = "支付流水ID")
    private Long paymentId;

    @Schema(description = "申请人")
    private Long userId;

    @Schema(description = "抬头类型（1个人 2企业）")
    private Integer titleType;

    @Schema(description = "发票抬头")
    private String title;

    @Schema(description = "纳税人识别号（企业必填）")
    private String taxNo;

    @Schema(description = "接收邮箱")
    private String email;

    @Schema(description = "开票金额")
    private BigDecimal amount;

    @Schema(description = "状态（0待开票 1已开票 2已红冲）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    @Schema(description = "开票时间")
    private LocalDateTime issueTime;
}
