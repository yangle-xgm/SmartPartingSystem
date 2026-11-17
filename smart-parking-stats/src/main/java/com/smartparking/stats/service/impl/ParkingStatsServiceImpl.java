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

    @Value("${smart-parking.parking-url:http://localhost:8082}")
    private String parkingUrl;

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

        // 当前在场车辆数（DEF-07）：以车位服务实时占用数为准，和"已占用 N"同源；
        // 车位服务不可用时退回订单服务进行中订单数，再退回当日进出场差值。
        long currentParked = queryOccupiedSpaces();
        if (currentParked < 0) {
            currentParked = queryOngoingCount();
        }
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

        // DEF-08：统计表里没有的日期原来直接丢点，导致"只有 1 天数据 + 平均利用率 0.0%"。
        // 现在按日期区间逐日补齐：缺失日占用记 0，总车位取当前车位数；今天缺失时用实时占用数兜底。
        Map<java.time.LocalDate, ParkingStats> byDate = new java.util.HashMap<>();
        for (ParkingStats item : statsList) {
            byDate.put(item.getStatDate(), item);
        }
        int[] live = querySpaceCounts();
        int fallbackTotal = live != null && live[0] > 0 ? live[0] : currentTotalSpaces(statsList);
        int liveOccupied = live != null ? Math.max(0, live[1]) : 0;
        LocalDate today = LocalDate.now();

        List<String> dates = new java.util.ArrayList<>();
        List<Integer> totalSpaces = new java.util.ArrayList<>();
        List<Integer> occupiedSpaces = new java.util.ArrayList<>();
        // 平均利用率只在"有统计口径的日期"上算：缺失日补 0 只是为了图表连续，
        // 若把它们算进平均会把整体拉成接近 0（原来就是 0.0%）。
        java.util.List<Double> validRates = new java.util.ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            ParkingStats row = byDate.get(day);
            int total = fallbackTotal;
            int occupied = 0;
            boolean hasData = row != null;
            if (row != null) {
                if (row.getTotalSpaces() != null && row.getTotalSpaces() > 0) {
                    total = row.getTotalSpaces();
                }
                if (row.getOccupiedSpaces() != null) {
                    occupied = row.getOccupiedSpaces();
                }
            } else if (day.equals(today) && fallbackTotal > 0) {
                occupied = Math.min(liveOccupied, fallbackTotal);
                hasData = true;
            }
            dates.add(day.toString());
            totalSpaces.add(total);
            occupiedSpaces.add(occupied);
            if (hasData) {
                validRates.add(total > 0 ? occupied * 1.0 / total : 0.0);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("totalSpaces", totalSpaces);
        result.put("occupiedSpaces", occupiedSpaces);

        double avgOccupancy = validRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        result.put("avgOccupancy", Math.round(avgOccupancy * 100) / 100.0);
        result.put("liveOccupied", liveOccupied);
        result.put("liveTotal", fallbackTotal);

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

        // E2E 报告 P1-2：占用率统计恒为 0 —— 新建行时 totalSpaces/occupiedSpaces 置 0 后再无回填。
        // 每次更新都从车位服务拉真实车位数/占用数覆盖（失败保持原值，不误报）。
        backfillSpaceCounts(stats);

        this.saveOrUpdate(stats);

        // 清除缓存（占用率缓存键带日期后缀，需 keys 通配匹配；delete 单键不支持 *）
        redisTemplate.delete(DASHBOARD_KEY);
        try {
            java.util.Set<String> occupancyKeys = redisTemplate.keys(OCCUPANCY_KEY + ":*");
            if (occupancyKeys != null && !occupancyKeys.isEmpty()) {
                redisTemplate.delete(occupancyKeys);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 从车位服务回填总车位数与占用车位数（内部接口 /api/parking/internal/occupancy-summary）。
     * 失败时保持原值，避免统计服务被车位服务故障拖垮。
     */
    private void backfillSpaceCounts(ParkingStats stats) {
        try {
            java.util.Map<String, Object> resp = restTemplate.getForObject(
                    parkingUrl + "/api/parking/internal/occupancy-summary", java.util.Map.class);
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof java.util.Map<?, ?> m) {
                Object total = m.get("total");
                Object occupied = m.get("occupied");
                if (total instanceof Number) {
                    stats.setTotalSpaces(((Number) total).intValue());
                }
                if (occupied instanceof Number) {
                    stats.setOccupiedSpaces(((Number) occupied).intValue());
                }
            }
        } catch (Exception e) {
            log.warn("回填车位数/占用数失败，保持原值：{}", e.getMessage());
        }
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
     * 查询车位服务实时占用数；失败返回 -1（调用方回退其他口径）
     */
    private long queryOccupiedSpaces() {
        int[] counts = querySpaceCounts();
        return counts == null ? -1 : Math.max(0, counts[1]);
    }

    /**
     * 车位服务实时总车位/占用数（内部接口 /api/parking/internal/occupancy-summary）；失败返回 null
     */
    private int[] querySpaceCounts() {
        try {
            java.util.Map<String, Object> resp = restTemplate.getForObject(
                    parkingUrl + "/api/parking/internal/occupancy-summary", java.util.Map.class);
            Object data = resp == null ? null : resp.get("data");
            if (data instanceof java.util.Map<?, ?> m) {
                int total = m.get("total") instanceof Number t ? t.intValue() : 0;
                int occupied = m.get("occupied") instanceof Number o ? o.intValue() : 0;
                if (total > 0 || occupied > 0) {
                    return new int[]{total, occupied};
                }
            }
        } catch (Exception e) {
            log.warn("查询车位实时占用失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 统计表里最近一次的总车位数（车位服务不可用时兜底）
     */
    private int currentTotalSpaces(List<ParkingStats> statsList) {
        int total = 0;
        for (ParkingStats s : statsList) {
            if (s.getTotalSpaces() != null && s.getTotalSpaces() > 0) {
                total = s.getTotalSpaces();
            }
        }
        return total;
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
