package com.smartparking.vehicle.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月卡订单（预付费套餐单，区别于按时计费的停车订单）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monthly_card_order")
@Schema(description = "月卡订单")
public class MonthlyCardOrder extends BaseEntity {

    @Schema(description = "月卡订单号")
    private String orderNo;

    @Schema(description = "业主ID")
    private Long userId;

    @Schema(description = "车辆ID")
    private Long vehicleId;

    @Schema(description = "车牌（密文）")
    private String plateNo;

    @Schema(description = "购买月数")
    private Integer months;

    @Schema(description = "下单时月单价快照")
    private BigDecimal unitPrice;

    @Schema(description = "订单金额")
    private BigDecimal amount;

    @Schema(description = "支付方式（1微信 2支付宝）")
    private Integer payType;

    @Schema(description = "支付状态（0待支付 1成功 2失败 3已取消）")
    private Integer payStatus;

    @Schema(description = "支付流水号")
    private String payNo;

    @Schema(description = "第三方交易号")
    private String transactionId;

    @Schema(description = "支付前月卡到期时间")
    private LocalDateTime expireBefore;

    @Schema(description = "支付后月卡到期时间")
    private LocalDateTime expireAfter;

    @Schema(description = "支付完成时间")
    private LocalDateTime payTime;
}
