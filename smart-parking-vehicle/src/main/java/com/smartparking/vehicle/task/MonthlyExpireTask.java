package com.smartparking.vehicle.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 月卡过期处理：每天凌晨2点检查，过期月卡车自动转为临时车(type=0)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyExpireTask {

    private final VehicleService vehicleService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void expireMonthlyCards() {
        List<Vehicle> expired = vehicleService.list(
                new LambdaQueryWrapper<Vehicle>()
                        .eq(Vehicle::getType, 2)
                        .isNotNull(Vehicle::getMonthlyExpire)
                        .lt(Vehicle::getMonthlyExpire, LocalDateTime.now()));
        for (Vehicle v : expired) {
            v.setType(0);
            vehicleService.updateById(v);
            log.info("月卡到期自动转为临时车：vehicleId={}, plate={}", v.getId(), v.getPlateNo());
        }
    }
}
