package com.smartparking.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单自动补偿任务
 * 每10分钟扫描进行中超过24小时的订单，自动结算并释放车位
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
                // 1. 补算费用：从入场时间到当前
                long minutes = Duration.between(order.getEntryTime(), LocalDateTime.now()).toMinutes();
                order.setDuration((int) minutes);
                // 简化计费：1元/小时，每日上限30元
                long hours = Math.max(1, minutes / 60);
                BigDecimal amount = BigDecimal.valueOf(Math.min(hours, 30));
                order.setAmount(amount);
                order.setExitTime(LocalDateTime.now());
                order.setStatus(2); // 已完成
                parkingOrderService.updateById(order);
                log.info("超时订单自动补偿：orderNo={}, duration={}min, amount={}",
                        order.getOrderNo(), minutes, amount);
            } catch (Exception e) {
                log.error("补偿订单{}失败：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }
}
