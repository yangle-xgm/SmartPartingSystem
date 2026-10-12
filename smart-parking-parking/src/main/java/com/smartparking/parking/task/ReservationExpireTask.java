package com.smartparking.parking.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.parking.mapper.ReservationMapper;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.po.Reservation;
import com.smartparking.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约到期自动释放：预约结束时间已过但车辆未入场（status=0），30分钟后自动释放车位
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpireTask {

    private final ReservationMapper reservationMapper;
    private final ParkingSpaceService parkingSpaceService;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void releaseExpiredReservations() {
        // 找已预约且结束时间已过30分钟的记录
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Reservation> expired = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStatus, 0)
                        .lt(Reservation::getEndTime, threshold));
        for (Reservation r : expired) {
            // 释放车位
            parkingSpaceService.update(new LambdaUpdateWrapper<ParkingSpace>()
                    .eq(ParkingSpace::getId, r.getSpaceId())
                    .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_RESERVED)
                    .set(ParkingSpace::getStatus, Constants.SPACE_STATUS_FREE)
                    .set(ParkingSpace::getOwnerId, null));
            // 更新预约状态
            r.setStatus(2);
            reservationMapper.updateById(r);
            log.info("预约到期自动释放：spaceId={}, userId={}", r.getSpaceId(), r.getUserId());
        }
    }
}
