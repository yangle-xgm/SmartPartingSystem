package com.smartparking.order.task;

import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时补偿定时任务：每 5 分钟扫描「已出场但长时间未付款」的订单。
 *
 * <p>报告 P0-7：原实现以 {@code create_time} 判超时，且直接 {@code setStatus(2)}（已取消）。
 * 停 40 分钟的车出场结算时 create_time 早已超过 30 分钟，几分钟内订单就被取消，
 * 而取消后 confirmPayment 拒绝支付 —— 服务已交付、应收直接丢失。
 *
 * <p>现改为：
 * <ol>
 *   <li>超时基准取 {@code exit_time}（结算时间），而非入场/建单时间；</li>
 *   <li>只把 {@code pay_status} 置为「已关闭(3)」并**保留** ORDER_STATUS_COMPLETED，
 *       不再制造「已完成 → 已取消」的非法跳转，应收仍在账上可追缴；</li>
 *   <li>条件更新（status=1 AND pay_status=0），并发下不会覆盖用户正在发起的支付。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutTask {

    private final ParkingOrderService parkingOrderService;

    /** 出场后允许的支付窗口（分钟） */
    private static final long PAY_WINDOW_MINUTES = 30;

    @Scheduled(cron = "0 */5 * * * ?")
    public void checkPaymentTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PAY_WINDOW_MINUTES);

        List<ParkingOrder> timeoutOrders = parkingOrderService.lambdaQuery()
                .eq(ParkingOrder::getStatus, com.smartparking.common.core.constant.Constants.ORDER_STATUS_COMPLETED)
                .eq(ParkingOrder::getPayStatus, com.smartparking.common.core.constant.Constants.PAY_STATUS_UNPAID)
                .le(ParkingOrder::getExitTime, deadline)
                .last("LIMIT 200")
                .list();

        if (timeoutOrders.isEmpty()) {
            return;
        }

        int closed = 0;
        for (ParkingOrder order : timeoutOrders) {
            try {
                if (parkingOrderService.markPaymentClosed(order.getId())) {
                    closed++;
                    log.info("订单{}出场超过{}分钟未支付，标记为已关闭（应收保留）",
                            order.getOrderNo(), PAY_WINDOW_MINUTES);
                }
            } catch (Exception e) {
                log.error("处理订单{}支付超时失败：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }
        log.info("支付超时检查完成，扫描{}条，关闭{}条", timeoutOrders.size(), closed);
    }
}
