package com.smartparking.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.stats.po.ParkingStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计Mapper
 */
@Mapper
public interface ParkingStatsMapper extends BaseMapper<ParkingStats> {

    /**
     * 查询指定日期的统计数据
     */
    List<ParkingStats> selectByDate(@Param("date") LocalDate date);

    /**
     * 查询日期范围内的总收入
     */
    BigDecimal sumIncomeByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 按小时统计入场数量（用于高峰时段分析）
     */
    List<Map<String, Object>> selectEntryCountByHour(@Param("date") LocalDate date);
}
