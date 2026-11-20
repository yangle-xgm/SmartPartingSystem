package com.smartparking.order.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 欠费黑名单判定：次数超 N 或累计超 M 即拉黑，入场由车辆服务拦停。
 */
@DisplayName("欠费超限判定")
class ArrearagePolicyTest {

    private static final int COUNT_LIMIT = 3;
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("100.00");

    private static boolean exceeded(int count, String total) {
        return ArrearageScanTask.exceedsLimit(count, new BigDecimal(total), COUNT_LIMIT, AMOUNT_LIMIT);
    }

    @Test
    @DisplayName("次数与金额都没超：不拉黑")
    void withinLimits_notBlocked() {
        assertFalse(exceeded(0, "0.00"));
        assertFalse(exceeded(1, "3.00"));
        assertFalse(exceeded(3, "99.99"));
    }

    @Test
    @DisplayName("次数严格大于阈值才拉黑：等于 3 次仍放行")
    void countBoundary() {
        assertFalse(exceeded(3, "10.00"));
        assertTrue(exceeded(4, "10.00"));
    }

    @Test
    @DisplayName("累计金额严格大于阈值才拉黑：恰好 100.00 不拉黑")
    void amountBoundary() {
        assertFalse(exceeded(2, "100.00"));
        assertTrue(exceeded(2, "100.01"));
    }

    @Test
    @DisplayName("任一项超限即拉黑")
    void eitherExceeds_blocks() {
        assertTrue(exceeded(9, "1.00"));
        assertTrue(exceeded(1, "500.00"));
    }
}
