package com.smartparking.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.query.ParkingOrderQuery;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 停车订单服务接口
 */
public interface ParkingOrderService extends IService<ParkingOrder> {

    /**
     * 车辆入场，创建订单
     *
     * @param vehicleType 车辆类型（0私家车 1访客车辆 2月租车），用于匹配计费规则
     * @param plateColor  车牌颜色（如"绿"），用于匹配新能源优惠规则，可为null
     */
    ParkingOrder createEntryOrder(Long vehicleId, String plateNo, Long spaceId, String spaceNo,
                                  Integer vehicleType, String plateColor);

    /**
     * 车辆出场，完成订单
     */
    ParkingOrder completeOrder(String orderNo);

    /**
     * 分页查询订单列表
     */
    Page<ParkingOrder> pageList(Page<ParkingOrder> page, ParkingOrderQuery query);

    /**
     * 获取当前停车中的订单
     */
    ParkingOrder getCurrentOrder(Long vehicleId);

    /**
     * 实时计算停车费用
     */
    BigDecimal calculateAmount(String orderNo);

    /**
     * 支付回调确认
     */
    void confirmPayment(Long orderId, BigDecimal amount);

    /**
     * 取消订单（车主取消/管理端取消）：关闭订单并释放车位
     */
    void cancelOrder(String orderNo);

    /**
     * 支付超时关闭订单（支付超时补偿定时任务调用）：关闭订单并释放车位
     */
    void timeoutClose(Long orderId);

    /**
     * 退款回调：订单支付状态置为已退款（不允许再次支付）
     */
    void refundOrder(Long orderId);

    /**
     * 查询订单信息（供支付服务校验归属/状态与分润计算使用）
     *
     * @return id/orderNo/spaceId/status/payStatus/amount
     */
    Map<String, Object> getOrderInfo(Long orderId);

    /**
     * 设备联动入场（MQTT摄像头识别→自动入场）
     * 识别注册车辆 → 分配空闲车位 → 创建入场订单
     *
     * @return 识别与入场结果（含 message；未命中时不创建订单）
     */
    Map<String, Object> deviceEntry(String plateNo, Integer confidence, String deviceNo);

    /**
     * 设备联动出场（出口摄像头识别→自动结算→释放车位）
     *
     * @return 结算结果（success/orderNo/amount/spaceNo；无进行中订单时不结算）
     */
    Map<String, Object> deviceExit(String plateNo, String deviceNo);
}
