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
     * 模拟支付：标记订单已支付，并把车辆月卡有效期延长到订单承诺的时间。
     * 幂等：重复支付同一订单直接拒绝，不会二次延长。
     */
    Map<String, Object> pay(String orderNo, Long currentUserId, String currentRole);

    /**
     * 我的月卡订单（按创建时间倒序）
     */
    List<MonthlyCardOrder> myOrders(Long userId);
}
