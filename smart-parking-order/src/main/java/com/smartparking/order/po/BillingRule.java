package com.smartparking.order.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.smartparking.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 计费规则实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("billing_rule")
@Schema(description = "计费规则")
public class BillingRule extends BaseEntity {

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "适用车辆类型（0全部 1临时车 2月租车）")
    private Integer vehicleType;

    @Schema(description = "适用车牌颜色（NULL表示全部）")
    private String plateColor;

    @Schema(description = "免费时长（分钟）")
    private Integer freeMinutes;

    @Schema(description = "单位价格（元）")
    private BigDecimal unitPrice;

    @Schema(description = "计费单位（分钟）")
    private Integer unitDuration;

    @Schema(description = "每日封顶金额")
    private BigDecimal dailyCap;

    @Schema(description = "月租价格")
    private BigDecimal monthlyPrice;

    @Schema(description = "周末单位价格（NULL则用unitPrice）")
    private BigDecimal weekendUnitPrice;

    @Schema(description = "周末每日封顶（NULL则用dailyCap）")
    private BigDecimal weekendDailyCap;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;
}
