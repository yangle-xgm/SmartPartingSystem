package com.smartparking.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计费规则新增/修改入参。
 *
 * <p>此前 billing_rule 只有一条对外只读接口，规则只能靠 SQL 手改 —— 计费是资损核心，
 * 运营不能在界面上调价导致每次都要 DBA 介入，且改错无从校验。此 DTO 承担入参校验。</p>
 */
@Data
@Schema(description = "计费规则入参")
public class BillingRuleDTO {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 50, message = "规则名称不能超过 50 字")
    @Schema(description = "规则名称")
    private String ruleName;

    @NotNull(message = "适用车辆类型不能为空")
    @Min(value = 0, message = "车辆类型取值 0/1/2")
    @Max(value = 2, message = "车辆类型取值 0/1/2")
    @Schema(description = "适用车辆类型（0全部 1临时车 2月租车）")
    private Integer vehicleType;

    @Size(max = 10, message = "车牌颜色描述过长")
    @Schema(description = "适用车牌颜色（空表示全部）")
    private String plateColor;

    @NotNull(message = "免费时长不能为空")
    @Min(value = 0, message = "免费时长不能为负")
    @Max(value = 1440, message = "免费时长不能超过 24 小时")
    @Schema(description = "免费时长（分钟）")
    private Integer freeMinutes;

    @NotNull(message = "单位价格不能为空")
    @DecimalMin(value = "0.01", message = "单位价格必须大于 0")
    @Schema(description = "单位价格（元）")
    private BigDecimal unitPrice;

    @NotNull(message = "计费单位不能为空")
    @Min(value = 1, message = "计费单位至少 1 分钟")
    @Max(value = 1440, message = "计费单位不能超过 24 小时")
    @Schema(description = "计费单位（分钟）")
    private Integer unitDuration;

    @DecimalMin(value = "0.00", message = "每日封顶不能为负")
    @Schema(description = "每日封顶金额（自然日，空表示不封顶）")
    private BigDecimal dailyCap;

    @DecimalMin(value = "0.00", message = "月租价格不能为负")
    @Schema(description = "月租价格（月租车规则必填）")
    private BigDecimal monthlyPrice;

    @DecimalMin(value = "0.00", message = "周末单位价格不能为负")
    @Schema(description = "周末单位价格（空则用 unitPrice）")
    private BigDecimal weekendUnitPrice;

    @DecimalMin(value = "0.00", message = "周末封顶不能为负")
    @Schema(description = "周末每日封顶（空则用 dailyCap）")
    private BigDecimal weekendDailyCap;

    @Min(value = 0, message = "状态取值 0/1")
    @Max(value = 1, message = "状态取值 0/1")
    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;
}
