package com.smartparking.order.service;

import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.order.po.BillingRule;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.impl.ParkingOrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 计费单元测试。
 *
 * <p>报告 P2-18：本文件原先在测试里**重写了一份计费公式**再验证自己 —— 生产
 * {@code calculateBillingAmount} 无论怎么改，测试都照样绿，等于没有回归保护。
 * 现在改为直接调用生产方法（反射拿 private），断言的是真实实现。
 */
class BillingAndErrorCodeTest {

    private final BillingRuleService billingRuleService = mock(BillingRuleService.class);

    /** 只注入计费规则服务；其余依赖在本地计费路径上不会被触达 */
    private final ParkingOrderServiceImpl service = new ParkingOrderServiceImpl(
            billingRuleService, null, null, null, null);

    /** 周一 08:00，固定入场时刻让「自然日切段」结果可复现 */
    private static final LocalDateTime MON_0800 = LocalDateTime.of(2026, 11, 16, 8, 0);

    private BigDecimal calc(long totalMinutes, int freeMinutes, int unitDuration,
                            BigDecimal unitPrice, BigDecimal dailyCap) {
        return calc(totalMinutes, freeMinutes, unitDuration, unitPrice, dailyCap, null, MON_0800);
    }

    private BigDecimal calc(long totalMinutes, int freeMinutes, int unitDuration,
                            BigDecimal unitPrice, BigDecimal dailyCap,
                            BigDecimal weekendUnitPrice, LocalDateTime entryTime) {
        BillingRule rule = new BillingRule();
        rule.setId(1L);
        rule.setVehicleType(1);            // 临停，避免走月租车远程判定分支
        rule.setFreeMinutes(freeMinutes);
        rule.setUnitDuration(unitDuration);
        rule.setUnitPrice(unitPrice);
        rule.setDailyCap(dailyCap);
        rule.setWeekendUnitPrice(weekendUnitPrice);
        when(billingRuleService.getById(1L)).thenReturn(rule);

        ParkingOrder order = new ParkingOrder();
        order.setOrderNo("ORD-TEST");
        order.setBillingRuleId(1L);
        order.setEntryTime(entryTime);

        return ReflectionTestUtils.invokeMethod(service, "calculateBillingAmount", order, totalMinutes);
    }

    @Test
    @DisplayName("免费时长内不计费")
    void freeTime_shouldBeZero() {
        assertEquals(new BigDecimal("0.00"), calc(25, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    @DisplayName("超出免费时长按单位向上取整")
    void overFreeTime_ceilToUnit() {
        assertEquals(new BigDecimal("2.50"), calc(35, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    @DisplayName("多单位累计")
    void multipleUnits() {
        assertEquals(new BigDecimal("5.00"), calc(90, 30, 30, new BigDecimal("2.5"), null));
    }

    @Test
    @DisplayName("跨自然日不按整块封顶多收（P1-10：24h30m 计费 1440min 应为 60.00，而非封顶 200）")
    void crossDay_shouldNotChargeFullCapPerBlock() {
        // 周一 08:00 入场，停 24h30m 到周二 08:30；扣 30min 免费后实际计费 1440min = 24 单位 = 60.00。
        // 旧「滚动块」实现按段各自 ceil，多收成 62.50；旧旧实现在此直接收满 dailyCap=200。
        BigDecimal r = calc(24 * 60 + 30, 30, 60, new BigDecimal("2.5"), new BigDecimal("200"));
        assertEquals(new BigDecimal("60.00"), r);
    }

    @Test
    @DisplayName("同一自然日内受封顶约束（08:00 停 14h = 35 元，封顶 30）")
    void singleBlock_cappedAtDayCap() {
        // 14h 全落在同一个自然日（08:00→22:00）：14 单位 × 2.5 = 35 → 封顶 30.00
        assertEquals(new BigDecimal("30.00"), calc(14 * 60, 0, 60, new BigDecimal("2.5"), new BigDecimal("30")));
    }

    @Test
    @DisplayName("跨自然日时封顶逐日各算一次（20h 跨两天 = 30 + 10 = 40）")
    void crossDay_capAppliesPerDayNotOnceForWholeStay() {
        // 周一 08:00 → 周二 04:00：段1 960min→16 单位=40→封顶 30；段2 240min→4 单位=10
        assertEquals(new BigDecimal("40.00"), calc(20 * 60, 0, 60, new BigDecimal("2.5"), new BigDecimal("30")));
    }

    @Test
    @DisplayName("日封顶按自然日重置：48h × 封顶 30 = 30 + 30 + 20 = 80")
    void dailyCap_resetsByNaturalDay() {
        // 周一 08:00 入场，停 48h 到周三 08:00。段1 960min(16 单位=40,封顶 30)、
        // 段2 1440min(24 单位=60,封顶 30)、段3 480min(8 单位=20)，合计 80.00。
        BigDecimal r = calc(48 * 60, 0, 60, new BigDecimal("2.5"), new BigDecimal("30"));
        assertEquals(new BigDecimal("80.00"), r);
    }

    @Test
    @DisplayName("跨周末按段所在自然日判费率：周六(5.0)+周日(5.0)+周一(2.5) = 220")
    void weekendRate_appliesPerNaturalDay() {
        // 2026-11-14 是周六，11-16 是周一
        LocalDateTime sat0800 = LocalDateTime.of(2026, 11, 14, 8, 0);
        // 段1 周六 960min→16 单位×5=80；段2 周日 1440→24×5=120；段3 周一 480→8×2.5=20
        BigDecimal r = calc(48 * 60, 0, 60, new BigDecimal("2.5"), null,
                new BigDecimal("5.0"), sat0800);
        assertEquals(new BigDecimal("220.00"), r);
    }

    @Test
    @DisplayName("计费规则缺失时不得静默计 0 元（P2-7：否则等于免费放行）")
    void missingBillingRule_shouldThrow() {
        when(billingRuleService.getById(999L)).thenReturn(null);
        ParkingOrder order = new ParkingOrder();
        order.setOrderNo("ORD-NO-RULE");
        order.setBillingRuleId(999L);
        order.setEntryTime(LocalDateTime.now());

        assertThrows(com.smartparking.common.core.exception.BizException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "calculateBillingAmount", order, 120L),
                "规则缺失必须抛错，不能返回 0");

        ParkingOrder noRuleId = new ParkingOrder();
        noRuleId.setOrderNo("ORD-NULL-RULE");
        noRuleId.setBillingRuleId(null);
        noRuleId.setEntryTime(LocalDateTime.now());
        assertThrows(com.smartparking.common.core.exception.BizException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "calculateBillingAmount", noRuleId, 120L),
                "billingRuleId 为空也必须抛错");
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
