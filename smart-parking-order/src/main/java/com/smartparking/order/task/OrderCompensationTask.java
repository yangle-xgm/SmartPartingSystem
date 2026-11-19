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
 * 超时订单自动补偿任务
 * 每10分钟扫描进行中超过24小时的订单，按统一计费规则自动结算并释放车位
 *
 * <p>报告 P0-6：原实现用「1 元/小时、上限 30」的野公式绕过免费时长/计费规则/日封顶，
 * 且把 status 写成 2（= 已取消），已结算订单因此被统计/对账当成取消单。
 * 现改为复用 {@link ParkingOrderService#completeOrder(String)}，计费与状态全部走主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationTask {

    private final ParkingOrderService parkingOrderService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void compensateStaleOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<ParkingOrder> staleOrders = parkingOrderService.lambdaQuery()
                .eq(ParkingOrder::getStatus, 0) // 进行中
                .le(ParkingOrder::getEntryTime, threshold)
                .list();

        if (staleOrders.isEmpty()) return;
        log.info("发现{}条超时进行中订单，开始补偿", staleOrders.size());

        for (ParkingOrder order : staleOrders) {
            try {
                // 统一计费入口：内部会算时长、套计费规则、置 COMPLETED、释放车位、发 MQ
                ParkingOrder settled = parkingOrderService.completeOrder(order.getOrderNo());
                log.info("超时订单自动结算：orderNo={}, duration={}min, amount={}",
                        settled.getOrderNo(), settled.getDuration(), settled.getAmount());
            } catch (Exception e) {
                log.error("补偿订单{}失败：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
