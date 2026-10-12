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
     * 支付成功回调
     */
    void paymentSuccess(String payNo, String transactionId);

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
