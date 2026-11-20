package com.smartparking.payment.service.impl;

import com.smartparking.common.core.constant.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付流水业务类型分派（月卡纳入支付服务后新增的资金链路分岔口）。
 *
 * <p>威胁模型：{@code paymentSuccess} 按 {@code biz_type} 决定后续动作 ——
 * 停车单要「订单确认 + 分润 + 开闸」，月卡只「通知车辆续期」。
 * 一旦判错方向，月卡流水会拿 {@code orderId=null} 去调 order 的确认/分润/开闸，
 * 表现为「钱收了、月卡没续、日志一堆 500」，且不会有人工可感知的报错。
 *
 * <p>同时锁住历史数据兜底：{@code biz_type} 为 null 的老流水必须按停车单处理
 * （这些流水全部产生于本次改造之前，order_id 一定有值）。
 */
class PaymentBizTypeRoutingTest {

    @Test
    @DisplayName("biz_type=2 识别为月卡：只通知车辆续期，不进订单/分润/开闸分支")
    void monthlyBiz_routedToVehicle() {
        assertTrue(PaymentRecordServiceImpl.isMonthlyBiz(Constants.BIZ_TYPE_MONTHLY_CARD));
        assertTrue(PaymentRecordServiceImpl.isMonthlyBiz(2));
    }

    @Test
    @DisplayName("biz_type=1 识别为停车订单：走订单确认 + 分润 + 开闸")
    void parkingBiz_routedToOrder() {
        assertFalse(PaymentRecordServiceImpl.isMonthlyBiz(Constants.BIZ_TYPE_PARKING_ORDER));
        assertFalse(PaymentRecordServiceImpl.isMonthlyBiz(1));
    }

    @Test
    @DisplayName("biz_type 为 null（改造前的历史流水）按停车订单处理，避免被误判成月卡")
    void nullBizType_treatedAsParkingOrder() {
        assertFalse(PaymentRecordServiceImpl.isMonthlyBiz(null));
    }

    @Test
    @DisplayName("未知 biz_type 不当作月卡：宁可走停车链路（orderId 缺失会被服务端拒绝），也不吞掉")
    void unknownBizType_notTreatedAsMonthly() {
        assertFalse(PaymentRecordServiceImpl.isMonthlyBiz(99));
    }
}
