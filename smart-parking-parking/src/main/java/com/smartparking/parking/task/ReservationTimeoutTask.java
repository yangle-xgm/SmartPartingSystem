package com.smartparking.parking.task;

import com.smartparking.common.core.constant.Constants;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 预约超时释放任务：预约状态(status=2)超过 24 小时未入场的车位自动释放为空闲
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutTask {

    private final ParkingSpaceService parkingSpaceService;

    /** 每 10 分钟扫描一次 */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void releaseExpiredReservations() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        long updated = parkingSpaceService.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_RESERVED)
                .lt(ParkingSpace::getUpdateTime, threshold));
        parkingSpaceService.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingSpace>()
                .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_RESERVED)
                .lt(ParkingSpace::getUpdateTime, threshold)
                .set(ParkingSpace::getStatus, Constants.SPACE_STATUS_FREE)
                .set(ParkingSpace::getOwnerId, null));
        if (updated > 0) {
            log.info("预约超时释放：{} 个车位已恢复空闲", updated);
        }
    }
}
