package com.smartparking.payment.service.impl;

import com.smartparking.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道结算金额一致性闸门（对接支付宝沙箱后新增的资金安全点）。
 *
 * <p>威胁模型：渠道回调/查询回来的 total_amount 与实际应付不一致（改价、串单、
 * 渠道侧误操作），若直接置成功，等于按渠道金额核销本地订单 —— 少收的钱再也追不回来。
 * 这里锁死：不一致必须抛错，一致才放行。
 */
class AlipaySettleAmountGuardTest {

    private static final String PAY_NO = "PAY202611191000001234";

    @Test
    @DisplayName("渠道金额少于应付 → 拒绝入账")
    void rejectWhenChannelAmountLess() {
        BizException ex = assertThrows(BizException.class, () -> PaymentRecordServiceImpl
                .assertAmountMatches(PAY_NO, new BigDecimal("30.00"), new BigDecimal("0.01")));
        assertTrue(ex.getMessage().contains("不一致"), "错误信息应说明金额不一致，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("渠道金额多于应付 → 同样拒绝（不接受多收导致的账务错位）")
    void rejectWhenChannelAmountMore() {
        assertThrows(BizException.class, () -> PaymentRecordServiceImpl
                .assertAmountMatches(PAY_NO, new BigDecimal("30.00"), new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("渠道未回传金额 → 拒绝入账")
    void rejectWhenChannelAmountMissing() {
        assertThrows(BizException.class, () -> PaymentRecordServiceImpl
                .assertAmountMatches(PAY_NO, new BigDecimal("30.00"), null));
    }

    @Test
    @DisplayName("金额数值相等（标度不同）→ 放行")
    void acceptWhenAmountEqual() {
        assertDoesNotThrow(() -> PaymentRecordServiceImpl
                .assertAmountMatches(PAY_NO, new BigDecimal("30.0"), new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("0 元渠道回传 vs 应付 30 元 → 拒绝（免费放行漏洞回归）")
    void rejectZeroAmount() {
        assertThrows(BizException.class, () -> PaymentRecordServiceImpl
                .assertAmountMatches(PAY_NO, new BigDecimal("30.00"), BigDecimal.ZERO));
    }
}
