package com.smartparking.vehicle.service;

import com.smartparking.vehicle.po.MonthlyCardOrder;

import java.util.List;
import java.util.Map;

/**
 * 月卡充值服务
 */
public interface MonthlyCardService {

    /**
     * 试算：给定车辆与购买月数，返回月单价、总额与充值后的到期时间（不落库）。
     * 前端选月数时实时调用，保证「看到的价」与「下单的价」同源。
     */
    Map<String, Object> quote(Long vehicleId, Integer months, Long currentUserId, String currentRole);

    /**
     * 创建月卡订单（待支付）。
     */
    MonthlyCardOrder createOrder(Long vehicleId, Integer months, Integer payType,
                                 Long currentUserId, String currentRole);

    /**
     * 按主键取月卡订单（内部接口用：支付服务创建流水前校验金额与状态）。
     */
    MonthlyCardOrder getMonthlyOrder(Long monthlyOrderId);

    /**
     * 支付到账后置为已支付并续期。
     *
     * <p>由支付服务的 {@code paymentSuccess} 在核销后调用（内部接口），<b>幂等</b>：
     * 同一订单重复回调只续期一次。此前这里是 vehicle 自己的 mock 自助通道，
     * 不产生支付流水、收入统计漏账，已废弃。
     *
     * @param payNo         支付服务生成的流水号（落库用于对账）
     * @param transactionId 第三方交易号
     */
    Map<String, Object> markPaid(Long monthlyOrderId, String payNo, String transactionId);

    /**
     * 我的月卡订单（按创建时间倒序）
     */
    List<MonthlyCardOrder> myOrders(Long userId);
}
