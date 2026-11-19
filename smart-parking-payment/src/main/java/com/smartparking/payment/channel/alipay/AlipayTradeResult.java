package com.smartparking.payment.channel.alipay;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝交易查询结果（只保留系统关心的字段）
 */
@Data
@Builder
public class AlipayTradeResult {

    /** WAIT_BUYER_PAY / TRADE_CLOSED / TRADE_SUCCESS / TRADE_FINISHED */
    private String tradeStatus;

    /** 支付宝交易号（32 位），入账时写入 payment_record.transaction_id */
    private String tradeNo;

    /** 渠道侧实收金额 */
    private BigDecimal totalAmount;

    /** 渠道原始返回（排查用） */
    private String raw;

    public boolean isPaid() {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }
}
