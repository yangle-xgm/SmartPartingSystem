package com.smartparking.payment.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.query.PaymentRecordQuery;

import java.math.BigDecimal;

/**
 * 支付记录服务接口
 */
public interface PaymentRecordService extends IService<PaymentRecord> {

    /**
     * 创建支付订单
     */
    PaymentRecord createPayment(Long orderId, Long userId, BigDecimal amount, Integer payType);

    /**
     * 创建月卡支付流水。
     *
     * <p>月卡与停车订单共用一张流水表，靠 {@code biz_type} 区分：停车单走
     * {@code orderId} + {@code confirm-payment}，月卡走 {@code bizId} + 车辆服务续期。
     * 金额与归属以车辆服务的月卡订单为权威，前端传值一律忽略。
     */
    PaymentRecord createMonthlyPayment(Long monthlyOrderId, Long userId, Integer payType);

    /**
     * 支付成功回调
     */
    void paymentSuccess(String payNo, String transactionId);

    /**
     * 渠道侧确认到账后结算（异步通知 / 主动查询共用）。
     *
     * <p>与裸调 {@link #paymentSuccess} 的区别：这里强制校验渠道金额与本地应付金额一致，
     * 不一致直接拒绝入账并告警 —— 防"1 分钱支付 100 元订单"这类改价攻击。
     *
     * @param channelAmount 渠道回传的实收金额
     * @return true 表示已置为已支付（含并发下已被其它线程置成功的幂等情形）
     */
    boolean settleFromChannel(String payNo, String transactionId, BigDecimal channelAmount);

    /**
     * 支付失败回调
     */
    void paymentFail(String payNo, String reason);

    /**
     * 退款
     */
    void refund(String payNo);

    /**
     * 分页查询支付记录
     */
    Page<PaymentRecord> pageList(Page<PaymentRecord> page, PaymentRecordQuery query);

    /**
     * 查询订单的支付记录
     */
    PaymentRecord getByOrderId(Long orderId);

    /**
     * 按支付流水号查询
     */
    PaymentRecord getByPayNo(String payNo);

    /**
     * 指定时间段内已支付金额合计（统计口径：以支付记录为准）
     */
    BigDecimal sumPaidAmount(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
