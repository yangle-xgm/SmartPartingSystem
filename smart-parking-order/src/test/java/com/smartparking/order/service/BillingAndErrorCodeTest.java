package com.smartparking.order.service;

import com.smartparking.common.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计费公式与错误码单元测试（不启动 Spring 上下文，纯逻辑）
 */
class BillingAndErrorCodeTest {

    /**
     * 与 ParkingOrderServiceImpl.calculateBillingAmount 保持一致的计费公式
     */
    private BigDecimal calc(long totalMinutes, long freeMinutes, long unitDuration,
                            BigDecimal unitPrice, BigDecimal dailyCap) {
        long billable = totalMinutes - freeMinutes;
        if (billable <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long units = (long) Math.ceil((double) billable / unitDuration);
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(units));
        if (dailyCap != null) {
            long days = totalMinutes / (24 * 60);
            long remain = totalMinutes % (24 * 60);
            if (days > 0) {
                BigDecimal dayAmount = dailyCap.multiply(BigDecimal.valueOf(days));
                long remBillable = remain - freeMinutes;
                if (remBillable > 0) {
                    long remUnits = (long) Math.ceil((double) remBillable / unitDuration);
                    BigDecimal remAmount = unitPrice.multiply(BigDecimal.valueOf(remUnits));
                    total = dayAmount.add(remAmount.min(dailyCap));
                } else {
                    total = dayAmount;
                }
            }
            total = total.min(dailyCap.multiply(BigDecimal.valueOf(Math.max(days + 1, 1))));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void freeTime_shouldBeZero() {
        // 30 分钟内免费：25 分钟应 0 元
        assertEquals(new BigDecimal("0.00"), calc(25, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    void overFreeTime_ceilToUnit() {
        // 35 分钟：超免费 5 分钟，按 30 分钟一个计费单位向上取整 = 1 单位 = 2.5 元
        assertEquals(new BigDecimal("2.50"), calc(35, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    void multipleUnits() {
        // 90 分钟：超免费 60 分钟 = 2 单位 = 5 元
        assertEquals(new BigDecimal("5.00"), calc(90, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    void dailyCap_cappedAtDayCap() {
        // 停 25 小时（跨两天），日封顶 50：第一天 50，第二天 1 小时 2.5，合计 52.5
        BigDecimal r = calc(25 * 60, 30, 30, new BigDecimal("2.5"), new BigDecimal("50"));
        assertEquals(new BigDecimal("52.50"), r);
    }

    @Test
    void errorCodes_areUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode c : ErrorCode.values()) {
            assertTrue(seen.add(c.getCode()), "错误码重复: " + c.getCode());
        }
    }

    @Test
    void errorCodes_haveMessages() {
        for (ErrorCode c : ErrorCode.values()) {
            assertNotNull(c.getMessage());
            assertFalse(c.getMessage().isBlank());
        }
    }
}
