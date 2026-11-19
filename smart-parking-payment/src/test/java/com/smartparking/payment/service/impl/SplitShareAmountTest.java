package com.smartparking.payment.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 共享车位分润金额拆分（7:3）。
 *
 * <p>锁死的核心不变量：<b>业主所得 + 物业所得 = 总额</b>。
 *
 * <p>回归背景：原实现让业主与物业各自乘比例后交给数据库按 decimal(10,2) 舍入，
 * 总额 0.05 元时得到 0.04 + 0.02 = 0.06，凭空多出 1 分钱；总额 0.01 元时
 * 得到 0.01 + 0.00，业主独吞全额。改为「业主四舍五入到分、物业取差额」后守恒。
 */
class SplitShareAmountTest {

    private static final BigDecimal RATIO = new BigDecimal("0.7");

    private static void assertSplit(String total, String expectOwner, String expectProperty) {
        BigDecimal[] r = PaymentRecordServiceImpl.splitShareAmount(new BigDecimal(total), RATIO);
        assertEquals(new BigDecimal(expectOwner), r[0],
                "总额 " + total + " 的业主所得错误");
        assertEquals(new BigDecimal(expectProperty), r[1],
                "总额 " + total + " 的物业所得错误");
        assertEquals(new BigDecimal(total), r[0].add(r[1]),
                "总额 " + total + " 分润后合计不等于总额（账不平）");
    }

    @Test
    @DisplayName("0.01 元：业主 0.01、物业 0.00，合计守恒")
    void splitOneCent() {
        assertSplit("0.01", "0.01", "0.00");
    }

    @Test
    @DisplayName("0.05 元：业主 0.04、物业 0.01（回归：修复前为 0.04+0.02=0.06）")
    void splitFiveCents() {
        assertSplit("0.05", "0.04", "0.01");
    }

    @Test
    @DisplayName("0.10 元：业主 0.07、物业 0.03")
    void splitTenCents() {
        assertSplit("0.10", "0.07", "0.03");
    }

    @Test
    @DisplayName("5.00 元：业主 3.50、物业 1.50")
    void splitFiveYuan() {
        assertSplit("5.00", "3.50", "1.50");
    }

    @Test
    @DisplayName("历史真实金额 21.00 元：业主 14.70、物业 6.30")
    void splitTwentyOneYuan() {
        assertSplit("21.00", "14.70", "6.30");
    }

    @Test
    @DisplayName("0.01~2.00 全区间逐分遍历，合计必须恒等于总额")
    void sumAlwaysEqualsTotal() {
        for (int cents = 1; cents <= 200; cents++) {
            BigDecimal total = BigDecimal.valueOf(cents, 2);
            BigDecimal[] r = PaymentRecordServiceImpl.splitShareAmount(total, RATIO);
            assertEquals(total, r[0].add(r[1]),
                    "总额 " + total + " 分润合计 " + r[0] + "+" + r[1] + " 不等于总额");
            assertEquals(total, r[0].add(r[1]).setScale(2, java.math.RoundingMode.HALF_UP),
                    "总额 " + total + " 分润合计标度异常");
        }
    }
}
