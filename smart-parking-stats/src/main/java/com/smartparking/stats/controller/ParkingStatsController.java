package com.smartparking.stats.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.stats.service.ParkingStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@Tag(name = "数据统计", description = "车位利用率、收入统计、高峰时段分析")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class ParkingStatsController {

    private final ParkingStatsService parkingStatsService;

    @Operation(summary = "首页看板数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = parkingStatsService.getDashboardData();
        return Result.success(data);
    }

    @Operation(summary = "车位利用率统计")
    @GetMapping("/occupancy")
    public Result<Map<String, Object>> occupancy(
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Map<String, Object> stats = parkingStatsService.getOccupancyStats(startDate, endDate);
        return Result.success(stats);
    }

    @Operation(summary = "收入统计")
    @GetMapping("/income")
    public Result<Map<String, Object>> income(
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Map<String, Object> stats = parkingStatsService.getIncomeStats(startDate, endDate);
        return Result.success(stats);
    }

    @Operation(summary = "高峰时段分析")
    @GetMapping("/peak")
    public Result<List<Map<String, Object>>> peak(
            @Parameter(description = "日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        List<Map<String, Object>> peakHours = parkingStatsService.getPeakHours(date);
        return Result.success(peakHours);
    }

    @Operation(summary = "报表导出（返回数据，前端生成Excel）")
    @GetMapping("/export")
    public Result<Map<String, Object>> export(
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        // 返回统计汇总数据，前端使用SheetJS/CSV导出Excel
        Map<String, Object> data = new HashMap<>();
        data.put("income", parkingStatsService.getIncomeStats(startDate, endDate));
        data.put("occupancy", parkingStatsService.getOccupancyStats(startDate, endDate));
        return Result.success(data);
    }

    // ==================== 内部接口 ====================

    @Operation(summary = "更新统计数据（内部接口）")
    @PostMapping("/internal/update")
    public Result<Void> updateStats(
            @Parameter(description = "类型") @RequestParam String type,
            @RequestBody Map<String, Object> data) {
        parkingStatsService.updateStats(type, data);
        return Result.success();
    }
}
