package com.smartparking.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时补偿定时任务
 * 每5分钟扫描待支付超时的订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutTask {

    private final ParkingOrderService parkingOrderService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void checkPaymentTimeout() {
        log.info("开始检查支付超时订单...");

        // 查询已完成但未支付超过30分钟的订单
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(30);
        List<ParkingOrder> timeoutOrders = parkingOrderService.lambdaQuery()
                .eq(ParkingOrder::getStatus, 1) // 已完成
                .eq(ParkingOrder::getPayStatus, 0) // 未支付
                .le(ParkingOrder::getCreateTime, timeout)
                .list();

        for (ParkingOrder order : timeoutOrders) {
            log.info("订单{}支付超时，尝试查询支付状态...", order.getOrderNo());
            try {
                // TODO: 调用微信/支付宝查询接口确认支付状态
                // boolean paid = paymentClient.queryPayStatus(order.getOrderNo());
                // if (paid) {
                //     parkingOrderService.confirmPayment(order.getOrderNo(), order.getAmount());
                // } else {
                //     // 标记为取消
                //     order.setStatus(2);
                //     parkingOrderService.updateById(order);
                // }

                // 简化处理：标记为取消
                order.setStatus(2);
                parkingOrderService.updateById(order);
                log.info("订单{}已标记为取消", order.getOrderNo());
            } catch (Exception e) {
                log.error("处理订单{}超时补偿失败：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }

        log.info("支付超时检查完成，处理{}条记录", timeoutOrders.size());
    }
}
