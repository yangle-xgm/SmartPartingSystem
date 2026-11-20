package com.smartparking.order.service.impl;

import com.smartparking.common.core.constant.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出场分级放行策略：出口不能因为"没付钱"直接堵死，也不能让大额欠费白嫖。
 * 阈值内先抬杆后付费，超出阈值必须当场付。
 */
@DisplayName("出场分级放行策略")
class GateReleasePolicyTest {

    private static final BigDecimal CREDIT = new BigDecimal("6.00");

    private static boolean release(Integer payStatus, String amount) {
        return release(payStatus, amount, CREDIT);
    }

    private static boolean release(Integer payStatus, String amount, BigDecimal limit) {
        return ParkingOrderServiceImpl.canRelease(payStatus, amount == null ? null : new BigDecimal(amount), limit);
    }

    @Test
    @DisplayName("已支付：无论如何都放行")
    void paid_alwaysReleases() {
        assertTrue(release(Constants.PAY_STATUS_PAID, "0.01"));
        assertTrue(release(Constants.PAY_STATUS_PAID, "500.00"));
    }

    @Test
    @DisplayName("免费订单（金额 0 或空）：直接放行，不需要支付")
    void free_alwaysReleases() {
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "0.00"));
        assertTrue(release(Constants.PAY_STATUS_UNPAID, null));
    }

    @Test
    @DisplayName("未支付且金额在信用额度内：先放行后付费")
    void unpaid_withinCredit_releases() {
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "0.01"));
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "5.99"));
        // 边界：恰好等于额度仍放行
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "6.00"));
    }

    @Test
    @DisplayName("未支付且金额超过信用额度：拦停，要求扫码支付")
    void unpaid_overCredit_blocks() {
        assertFalse(release(Constants.PAY_STATUS_UNPAID, "6.01"));
        assertFalse(release(Constants.PAY_STATUS_UNPAID, "12.50"));
        assertFalse(release(Constants.PAY_STATUS_UNPAID, "50.00"));
    }

    @Test
    @DisplayName("生效额度跟随规则单日封顶：小区长时停车不会被小时级阈值拦死")
    void effectiveLimitFollowsDailyCap() {
        // 临停：单日封顶 20 → 停满一天 20 元仍放行，跨日累积 40 元才拦
        BigDecimal limit = ParkingOrderServiceImpl.effectiveCreditLimit(
                CREDIT, new BigDecimal("20.00"), new BigDecimal("20.00"));
        assertEquals(new BigDecimal("20.00"), limit);
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "20.00", limit));
        assertFalse(release(Constants.PAY_STATUS_UNPAID, "40.00", limit));

        // 访客过夜：单日封顶 15 → 停一夜 15 元放行，第二天叠加到 30 元才拦
        BigDecimal visitor = ParkingOrderServiceImpl.effectiveCreditLimit(
                CREDIT, new BigDecimal("15.00"), null);
        assertEquals(new BigDecimal("15.00"), visitor);
        assertTrue(release(Constants.PAY_STATUS_UNPAID, "15.00", visitor));
        assertFalse(release(Constants.PAY_STATUS_UNPAID, "30.00", visitor));
    }

    @Test
    @DisplayName("周末封顶更高时取较大者：节假日出场不被平峰额度卡住")
    void effectiveLimitTakesHigherWeekendCap() {
        BigDecimal limit = ParkingOrderServiceImpl.effectiveCreditLimit(
                CREDIT, new BigDecimal("15.00"), new BigDecimal("25.00"));
        assertEquals(new BigDecimal("25.00"), limit);
    }

    @Test
    @DisplayName("规则无单日封顶（如月租）时回退配置兜底值")
    void effectiveLimitFallsBackWithoutCap() {
        assertEquals(CREDIT, ParkingOrderServiceImpl.effectiveCreditLimit(CREDIT, null, null));
        assertEquals(CREDIT, ParkingOrderServiceImpl.effectiveCreditLimit(CREDIT, BigDecimal.ZERO, null));
    }

    @Test
    @DisplayName("已退款视为未支付：按金额走信用额度判定")
    void refunded_followsAmountRule() {
        assertTrue(release(Constants.PAY_STATUS_REFUNDED, "3.00"));
        assertFalse(release(Constants.PAY_STATUS_REFUNDED, "30.00"));
    }

    @Test
    @DisplayName("支付状态为 null（历史脏数据）：按未支付处理，不因空值放行大额")
    void nullPayStatus_treatedAsUnpaid() {
        assertTrue(release(null, "2.00"));
        assertFalse(release(null, "20.00"));
    }
}
