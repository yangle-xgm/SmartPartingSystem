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

    /**
     * 把当天统计行的车位总数/占用数校准为车位服务的实时值。
     *
     * <p>车位数只在有车进出时才会回填，车位增删后若当天没有进出场事件，
     * {@code total_spaces} 会一直停在旧值（占用率分母偏大）。由定时任务周期调用。
     */
    void refreshTodaySpaceCounts();
}
