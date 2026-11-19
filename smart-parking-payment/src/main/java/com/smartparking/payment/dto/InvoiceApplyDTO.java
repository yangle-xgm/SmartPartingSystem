package com.smartparking.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发票申请入参（业主端）。
 */
@Data
@Schema(description = "发票申请")
public class InvoiceApplyDTO {

    @NotNull(message = "请选择要开票的订单")
    @Schema(description = "停车订单ID")
    private Long orderId;

    @NotNull(message = "请选择抬头类型")
    @Min(value = 1, message = "抬头类型取值 1个人 / 2企业")
    @Max(value = 2, message = "抬头类型取值 1个人 / 2企业")
    @Schema(description = "抬头类型（1个人 2企业）")
    private Integer titleType;

    @NotBlank(message = "请填写发票抬头")
    @Size(max = 100, message = "抬头不能超过 100 字")
    @Schema(description = "发票抬头")
    private String title;

    @Size(max = 30, message = "纳税人识别号不能超过 30 位")
    @Schema(description = "纳税人识别号（企业必填）")
    private String taxNo;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱过长")
    @Schema(description = "接收邮箱（电子发票发送地址）")
    private String email;
}
