package com.smartparking.stats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.stats.po.ParkingStats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计服务接口
 */
public interface ParkingStatsService extends IService<ParkingStats> {

    /**
     * 首页看板数据
     */
    Map<String, Object> getDashboardData();

    /**
     * 车位利用率统计
     */
    Map<String, Object> getOccupancyStats(LocalDate startDate, LocalDate endDate);

    /**
     * 收入统计
     */
    Map<String, Object> getIncomeStats(LocalDate startDate, LocalDate endDate);

    /**
     * 高峰时段分析
     */
    List<Map<String, Object>> getPeakHours(LocalDate date);

    /**
     * 更新统计数据（由MQ消息触发）
     */
    void updateStats(String type, Map<String, Object> data);
}
