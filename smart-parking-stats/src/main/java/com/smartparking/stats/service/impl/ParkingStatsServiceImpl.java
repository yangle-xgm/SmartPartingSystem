package com.smartparking.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.stats.mapper.ParkingStatsMapper;
import com.smartparking.stats.po.ParkingStats;
import com.smartparking.stats.service.ParkingStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingStatsServiceImpl extends ServiceImpl<ParkingStatsMapper, ParkingStats> implements ParkingStatsService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${smart-parking.order-url:http://localhost:8084}")
    private String orderUrl;

    @Value("${smart-parking.payment-url:http://localhost:8085}")
    private String paymentUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DASHBOARD_KEY = "smart:parking:stats:dashboard";
    private static final String OCCUPANCY_KEY = "smart:parking:stats:occupancy";

    @Override
    public Map<String, Object> getDashboardData() {
        // 优先从Redis获取
        Object cached = redisTemplate.opsForValue().get(DASHBOARD_KEY);
        if (cached instanceof Map) {
            return castToMap(cached);
        }

        Map<String, Object> dashboard = new HashMap<>();
        LocalDate today = LocalDate.now();

        // 今日入场数
        long todayEntry = this.baseMapper.selectByDate(today).stream()
                .mapToLong(ParkingStats::getEntryCount)
                .sum();
        dashboard.put("todayEntry", todayEntry);

        // 今日出场数
        long todayExit = this.baseMapper.selectByDate(today).stream()
                .mapToLong(ParkingStats::getExitCount)
                .sum();
        dashboard.put("todayExit", todayExit);

        // 今日/本月收入：以支付记录（已支付）为准，失败回退统计表
        BigDecimal todayIncome = queryPaidIncome(today, today);
        dashboard.put("todayIncome", todayIncome);
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal monthIncome = queryPaidIncome(monthStart, today);
        dashboard.put("monthIncome", monthIncome);

        // 当前在场车辆数：以订单服务进行中订单数为准（含昨日入场未出场车辆）
        long currentParked = queryOngoingCount();
        if (currentParked < 0) {
            currentParked = Math.max(0, todayEntry - todayExit);
        }
        dashboard.put("currentParked", currentParked);

        // 缓存5秒
        redisTemplate.opsForValue().set(DASHBOARD_KEY, dashboard, 5, TimeUnit.SECONDS);

        return dashboard;
    }

    @Override
    public Map<String, Object> getOccupancyStats(LocalDate startDate, LocalDate endDate) {
        String cacheKey = OCCUPANCY_KEY + ":" + startDate + ":" + endDate;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Map) {
            return castToMap(cached);
        }

        List<ParkingStats> statsList = this.list(new LambdaQueryWrapper<ParkingStats>()
                .ge(ParkingStats::getStatDate, startDate)
                .le(ParkingStats::getStatDate, endDate)
                .orderByAsc(ParkingStats::getStatDate));

        Map<String, Object> result = new HashMap<>();
        result.put("dates", statsList.stream().map(ParkingStats::getStatDate).toList());
        result.put("totalSpaces", statsList.stream().map(ParkingStats::getTotalSpaces).toList());
        result.put("occupiedSpaces", statsList.stream().map(ParkingStats::getOccupiedSpaces).toList());

        // 计算平均利用率
        double avgOccupancy = statsList.stream()
                .mapToDouble(s -> s.getTotalSpaces() == null || s.getTotalSpaces() == 0 ? 0.0 : s.getOccupiedSpaces() * 1.0 / s.getTotalSpaces())
                .average()
                .orElse(0.0);
        result.put("avgOccupancy", Math.round(avgOccupancy * 100) / 100.0);

        redisTemplate.opsForValue().set(cacheKey, result, 30, TimeUnit.SECONDS);
        return result;
    }

    @Override
    public Map<String, Object> getIncomeStats(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();

        // C2：收入以支付记录（已支付）为权威口径，失败回退统计表
        BigDecimal totalIncome = queryPaidIncome(startDate, endDate);
        result.put("totalIncome", totalIncome);

        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        result.put("avgDailyIncome", days > 0 ? totalIncome.divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("days", days);

        return result;
    }

    @Override
    public List<Map<String, Object>> getPeakHours(LocalDate date) {
        return this.baseMapper.selectEntryCountByHour(date);
    }

    @Override
    public void updateStats(String type, Map<String, Object> data) {
        log.info("更新统计数据：type={}, data={}", type, data);

        LocalDate today = LocalDate.now();
        ParkingStats stats = this.getOne(new LambdaQueryWrapper<ParkingStats>()
                .eq(ParkingStats::getStatDate, today));

        if (stats == null) {
            stats = new ParkingStats();
            stats.setStatDate(today);
            stats.setTotalSpaces(0);
            stats.setOccupiedSpaces(0);
            stats.setEntryCount(0);
            stats.setExitCount(0);
            stats.setTotalIncome(BigDecimal.ZERO);
            stats.setAvgDuration(0);
        }

        if ("ENTRY".equals(type)) {
            stats.setEntryCount(stats.getEntryCount() + 1);
        } else if ("EXIT".equals(type)) {
            stats.setExitCount(stats.getExitCount() + 1);
            // 更新收入
            Object amount = data.get("amount");
            if (amount instanceof BigDecimal) {
                stats.setTotalIncome(stats.getTotalIncome().add((BigDecimal) amount));
            }
        }

        this.saveOrUpdate(stats);

        // 清除缓存
        redisTemplate.delete(DASHBOARD_KEY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object cached) {
        return (Map<String, Object>) cached;
    }

    /**
     * 查询指定日期范围内已支付金额（支付服务权威口径）
     */
    private BigDecimal queryPaidIncome(LocalDate start, LocalDate end) {
        try {
            String url = paymentUrl + "/api/payment/internal/income-summary?startDate=" + start + "&endDate=" + end;
            java.util.Map<String, Object> resp = restTemplate.getForObject(url, java.util.Map.class);
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof java.util.Map<?, ?> m) {
                Object total = ((java.util.Map<?, ?>) m).get("total");
                if (total instanceof Number) {
                    return new BigDecimal(total.toString());
                }
            }
        } catch (Exception e) {
            log.warn("查询支付收入失败，回退统计表：{}", e.getMessage());
        }
        try {
            return this.baseMapper.sumIncomeByDateRange(start, end);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 查询当前进行中订单数（订单服务权威口径）；失败返回-1
     */
    private long queryOngoingCount() {
        try {
            String url = orderUrl + "/api/order/internal/ongoing-count";
            java.util.Map<String, Object> resp = restTemplate.getForObject(url, java.util.Map.class);
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof Number) {
                return ((Number) data).longValue();
            }
        } catch (Exception e) {
            log.warn("查询进行中订单数失败：{}", e.getMessage());
        }
        return -1;
    }
}
