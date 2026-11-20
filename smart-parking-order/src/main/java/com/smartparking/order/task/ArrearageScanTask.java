package com.smartparking.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.order.client.NotifyClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 欠费扫描：出口「信用放行」之后的三件事，一轮扫完。
 *
 * <p>为什么需要它：出口对小额未支付订单先抬杆（避免出口堵死），代价是欠费。
 * 本任务把欠费变成可追踪、可催缴、可拦截的资产：
 * <ol>
 *   <li>出场满 24h 未支付 → 发一次催缴通知（remind_time 去重，只发一次）；</li>
 *   <li>出场满 48h 未支付 → 标记 arrear_status=1，计入欠费统计；</li>
 *   <li>欠费次数 &gt; N 或 累计金额 &gt; M → 车辆置黑名单（status=1），入场即拦。</li>
 * </ol>
 *
 * <p>设计取舍：全部基于 parking_order 单表聚合，不新建欠费表 —— 欠费本质就是
 * "已完成且未支付"的订单视图，再建一张表只会引入双写不一致。黑名单直接复用
 * vehicle.status（车辆服务 recognize 处已有拦截逻辑），无需新增字段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArrearageScanTask {

    private final ParkingOrderService parkingOrderService;
    private final NotifyClient notifyClient;
    private final VehicleClient vehicleClient;

    @Value("${smart-parking.arrearage.remind-after-hours:24}")
    private int remindAfterHours;

    @Value("${smart-parking.arrearage.arrear-after-hours:48}")
    private int arrearAfterHours;

    @Value("${smart-parking.arrearage.count-limit:3}")
    private int countLimit;

    @Value("${smart-parking.arrearage.amount-limit:100.00}")
    private BigDecimal amountLimit;

    @Scheduled(cron = "0 */10 * * * ?")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        // 三步互相独立：任一步异常不能拖垮其它步骤
        try {
            remindUnpaid(now);
        } catch (Exception e) {
            log.error("待支付催缴扫描失败：{}", e.getMessage());
        }
        try {
            markArrears(now);
        } catch (Exception e) {
            log.error("欠费标记扫描失败：{}", e.getMessage());
        }
        try {
            refreshBlacklist();
        } catch (Exception e) {
            log.error("欠费黑名单刷新失败：{}", e.getMessage());
        }
    }

    /** 出场满 remindAfterHours 仍未支付 → 催缴一次（remind_time 保证只发一次） */
    private void remindUnpaid(LocalDateTime now) {
        List<ParkingOrder> orders = parkingOrderService.list(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_COMPLETED)
                .eq(ParkingOrder::getPayStatus, Constants.PAY_STATUS_UNPAID)
                .isNull(ParkingOrder::getRemindTime)
                .gt(ParkingOrder::getAmount, BigDecimal.ZERO)
                .le(ParkingOrder::getExitTime, now.minusHours(remindAfterHours))
                .last("LIMIT 200"));
        if (orders.isEmpty()) {
            return;
        }
        // 同一车主多笔欠费只查一次车辆服务
        Map<String, Long> ownerCache = new HashMap<>();
        int sent = 0;
        for (ParkingOrder order : orders) {
            Long ownerId = ownerOf(order, ownerCache);
            if (ownerId == null) {
                continue;
            }
            if (!notifyClient.sendPaymentReminder(ownerId, order.getOrderNo(), order.getAmount().toPlainString())) {
                continue;
            }
            // 投递成功才记时间，失败留到下一轮重试
            parkingOrderService.update(new LambdaUpdateWrapper<ParkingOrder>()
                    .eq(ParkingOrder::getId, order.getId())
                    .isNull(ParkingOrder::getRemindTime)
                    .set(ParkingOrder::getRemindTime, now));
            sent++;
        }
        if (sent > 0) {
            log.info("待支付催缴已发送 {} 笔（出场满 {} 小时未支付）", sent, remindAfterHours);
        }
    }

    /** 出场满 arrearAfterHours 仍未支付 → 记欠费（一条 UPDATE 批量完成） */
    private void markArrears(LocalDateTime now) {
        boolean updated = parkingOrderService.update(new LambdaUpdateWrapper<ParkingOrder>()
                .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_COMPLETED)
                .eq(ParkingOrder::getPayStatus, Constants.PAY_STATUS_UNPAID)
                .eq(ParkingOrder::getArrearStatus, 0)
                .gt(ParkingOrder::getAmount, BigDecimal.ZERO)
                .le(ParkingOrder::getExitTime, now.minusHours(arrearAfterHours))
                .set(ParkingOrder::getArrearStatus, 1));
        if (updated) {
            log.info("已标记欠费订单（出场满 {} 小时未支付）", arrearAfterHours);
        }
    }

    /** 欠费次数/累计金额超限 → 车辆置黑名单；车辆服务 recognize 会拒绝其入场 */
    private void refreshBlacklist() {
        List<ParkingOrder> rows = parkingOrderService.list(new LambdaQueryWrapper<ParkingOrder>()
                .select(ParkingOrder::getVehicleId, ParkingOrder::getAmount)
                .eq(ParkingOrder::getArrearStatus, 1)
                .eq(ParkingOrder::getPayStatus, Constants.PAY_STATUS_UNPAID));
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, Integer> countByVehicle = new HashMap<>();
        Map<Long, BigDecimal> amountByVehicle = new HashMap<>();
        for (ParkingOrder row : rows) {
            if (row.getVehicleId() == null) {
                continue;
            }
            BigDecimal amount = row.getAmount() == null ? BigDecimal.ZERO : row.getAmount();
            countByVehicle.merge(row.getVehicleId(), 1, Integer::sum);
            amountByVehicle.merge(row.getVehicleId(), amount, BigDecimal::add);
        }
        countByVehicle.forEach((vehicleId, count) -> {
            BigDecimal total = amountByVehicle.getOrDefault(vehicleId, BigDecimal.ZERO);
            if (exceedsLimit(count, total, countLimit, amountLimit)) {
                if (vehicleClient.updateStatus(vehicleId, 1)) {
                    log.warn("欠费超限列入黑名单 vehicleId={}, 欠费{}笔/共{}元", vehicleId, count, total);
                }
            }
        });
    }

    /** 欠费是否超限：次数严格大于 N，或累计金额严格大于 M（纯函数，便于单测） */
    static boolean exceedsLimit(int count, BigDecimal total, int countLimit, BigDecimal amountLimit) {
        return count > countLimit || total.compareTo(amountLimit) > 0;
    }

    /** 订单只存 vehicleId 与密文车牌，车主需经车辆服务反查；同车主复用缓存 */
    private Long ownerOf(ParkingOrder order, Map<String, Long> cache) {
        if (order.getPlateNo() == null) {
            return null;
        }
        return cache.computeIfAbsent(order.getPlateNo(), cipher -> {
            try {
                String plateNo = AesUtils.decrypt(cipher);
                if (plateNo == null) {
                    return null;
                }
                Object ownerId = vehicleClient.getByPlateNo(plateNo).get("ownerId");
                return ownerId == null ? null : Long.valueOf(String.valueOf(ownerId));
            } catch (Exception e) {
                log.warn("反查车主失败：{}", e.getMessage());
                return null;
            }
        });
    }
}
