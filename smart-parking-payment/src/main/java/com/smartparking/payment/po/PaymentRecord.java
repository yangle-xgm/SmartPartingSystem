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

    /**
     * 业务类型：1 停车订单 / 2 月卡。
     *
     * <p>月卡此前不经过支付服务（vehicle 自造 MOCK 流水），收入/发票/对账三条链路全部漏账；
     * 纳入本表后所有真金白银只有一个记账口径。字段与 {@code bizId} 成对使用。
     */
    @Schema(description = "业务类型（1停车订单 2月卡）")
    private Integer bizType;

    @Schema(description = "业务单ID（停车=parking_order.id 月卡=monthly_card_order.id）")
    private Long bizId;

    @Schema(description = "关联停车订单ID（月卡流水为 NULL）")
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
