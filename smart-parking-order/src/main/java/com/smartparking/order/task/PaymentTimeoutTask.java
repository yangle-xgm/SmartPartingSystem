package com.smartparking.order.task;

import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 出场后允许的支付窗口（分钟）。
     *
     * <p>原来是硬编码 {@code 30}，而 payment 侧同名语义的窗口是可配的
     * （{@code smart-parking.payment-timeout-minutes}），两边调整时极易只改一处：
     * 订单已关闭但流水还在等，用户点支付会拿到「订单已关闭」——现象看订单不敢看流水，
     * 排查方向直接跑偏。改成配置项后至少两边都有旋钮。
     *
     * <p>语义差异要注意：这里关的是**应收**（order.pay_status=3，状态仍为已完成，
     * 钱还挂着可追缴）；payment 那边关的是**支付流水**。
     */
    @Value("${smart-parking.order.pay-window-minutes:30}")
    private long payWindowMinutes = 30;

    @Scheduled(cron = "0 */5 * * * ?")
    public void checkPaymentTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payWindowMinutes);

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
                            order.getOrderNo(), payWindowMinutes);
                }
            } catch (Exception e) {
                log.error("处理订单{}支付超时失败：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }
        log.info("支付超时检查完成，扫描{}条，关闭{}条", timeoutOrders.size(), closed);
    }
}
