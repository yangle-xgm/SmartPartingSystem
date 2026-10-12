package com.smartparking.order.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车订单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_order")
@Schema(description = "停车订单")
public class ParkingOrder extends BaseEntity {

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "车辆ID")
    private Long vehicleId;

    @Schema(description = "车牌号（冗余）")
    private String plateNo;

    @Schema(description = "车位ID")
    private Long spaceId;

    @Schema(description = "车位号（冗余）")
    private String spaceNo;

    @Schema(description = "入场时间")
    private LocalDateTime entryTime;

    @Schema(description = "出场时间")
    private LocalDateTime exitTime;

    @Schema(description = "停车时长（分钟）")
    private Integer duration;

    @Schema(description = "计费规则ID")
    private Long billingRuleId;

    @Schema(description = "应付金额")
    private BigDecimal amount;

    @Schema(description = "支付状态（0未支付 1已支付 2已退款）")
    private Integer payStatus;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "订单状态（0进行中 1已完成 2已取消）")
    private Integer status;
}
