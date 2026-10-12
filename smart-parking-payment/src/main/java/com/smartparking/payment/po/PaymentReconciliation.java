package com.smartparking.payment.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支付对账记录
 */
@Data
@TableName("payment_reconciliation")
public class PaymentReconciliation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账日期 */
    private LocalDate reconDate;

    /** 支付笔数 */
    private Integer payCount;

    /** 支付总金额 */
    private BigDecimal totalAmount;

    /** 退款笔数 */
    private Integer refundCount;

    /** 退款总金额 */
    private BigDecimal refundAmount;

    /** 差额（订单应付 - 实际支付） */
    private BigDecimal diffAmount;

    /** 状态（0正常 1有差异） */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
