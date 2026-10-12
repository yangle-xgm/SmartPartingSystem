package com.smartparking.payment.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
@Schema(description = "支付记录")
public class PaymentRecord extends BaseEntity {

    @Schema(description = "支付流水号")
    private String payNo;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "支付用户ID")
    private Long userId;

    @Schema(description = "支付金额")
    private BigDecimal amount;

    @Schema(description = "支付方式（1微信 2支付宝 3现金）")
    private Integer payType;

    @Schema(description = "支付状态（0待支付 1成功 2失败 3退款）")
    private Integer payStatus;

    @Schema(description = "第三方交易号")
    private String transactionId;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;
}
