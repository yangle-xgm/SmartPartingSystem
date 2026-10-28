package com.smartparking.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.parking.mapper.ParkingSpaceMapper;
import com.smartparking.parking.mapper.ReservationMapper;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.po.Reservation;
import com.smartparking.parking.query.ParkingSpaceQuery;
import com.smartparking.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 车位服务实现
 */
@Service
@RequiredArgsConstructor
public class ParkingSpaceServiceImpl extends ServiceImpl<ParkingSpaceMapper, ParkingSpace> implements ParkingSpaceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Cache<String, Object> l1Cache;
    private final ReservationMapper reservationMapper;

    private static final String L1_STATS_KEY = "l1:parking:stats";

    /** 预约时长上限（小时），E2E 报告 P1-4：与前端同源，后端同样拦截 */
    @Value("${smart-parking.reserve-max-hours:2}")
    private int reserveMaxHours;

    @Override
    public Page<ParkingSpace> pageList(Page<ParkingSpace> page, ParkingSpaceQuery query, Long communityId) {
        LambdaQueryWrapper<ParkingSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getArea()), ParkingSpace::getArea, query.getArea())
                .eq(query.getType() != null, ParkingSpace::getType, query.getType())
                .eq(query.getStatus() != null, ParkingSpace::getStatus, query.getStatus())
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId)
                .orderByAsc(ParkingSpace::getSpaceNo);
        return this.page(page, wrapper);
    }

    @Override
    public List<ParkingSpace> findAvailableSpaces(String area, Long communityId) {
        LambdaQueryWrapper<ParkingSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_FREE)
                .eq(StringUtils.hasText(area), ParkingSpace::getArea, area)
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId)
                .orderByAsc(ParkingSpace::getSpaceNo);
        return this.list(wrapper);
    }

    @Override
    public List<ParkingSpace> findAllSpaces(String area, Long communityId) {
        LambdaQueryWrapper<ParkingSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParkingSpace::getIsDeleted, 0)
                .eq(StringUtils.hasText(area), ParkingSpace::getArea, area)
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId)
                .orderByAsc(ParkingSpace::getSpaceNo);
        return this.list(wrapper);
    }

    @Override
    public void updateStatus(Long spaceId, Integer status) {
        ParkingSpace space = this.getById(spaceId);
        if (space == null) {
            throw new BizException(ErrorCode.SPACE_NOT_FOUND);
        }
        space.setStatus(status);
        this.updateById(space);

        // 更新Redis缓存
        updateRedisSpaceStatus(space);
    }

    @Override
    public Map<String, Object> getOccupancySummary(Long communityId) {
        long total = this.count(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getIsDeleted, 0)
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId));
        long occupied = this.count(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getIsDeleted, 0)
                .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_OCCUPIED)
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId));
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("occupied", occupied);
        return summary;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAreaStatusStatistics(Long communityId) {
        // L1 本地缓存（10s），挡高频读，避免每次打 Redis；按小区隔离缓存键
        String l1Key = communityId == null ? L1_STATS_KEY : L1_STATS_KEY + ":" + communityId;
        Object l1 = l1Cache.getIfPresent(l1Key);
        if (l1 instanceof Map) return (Map<String, Object>) l1;

        // 优先从Redis获取
        String redisKey = communityId == null ? Constants.REDIS_PARKING_AVAILABLE
                : Constants.REDIS_PARKING_AVAILABLE + ":" + communityId;
        Map<Object, Object> cachedStats = redisTemplate.opsForHash().entries(redisKey);
        if (!cachedStats.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            cachedStats.forEach((k, v) -> result.put(String.valueOf(k), v));
            l1Cache.put(l1Key, result);
            return result;
        }

        // 从数据库查询
        List<Map<String, Object>> stats = this.baseMapper.selectAreaStatusStatistics();
        Map<String, Object> areaFreeCount = new HashMap<>();

        for (Map<String, Object> stat : stats) {
            String area = (String) stat.get("area");
            Integer status = (Integer) stat.get("status");
            Integer count = ((Number) stat.get("count")).intValue();

            if (Constants.SPACE_STATUS_FREE == status) {
                areaFreeCount.put(area, count);
                // 写入Redis，5秒过期
                redisTemplate.opsForHash().put(redisKey, area, count);
            }
        }

        // 设置Redis过期时间
        redisTemplate.expire(redisKey, 30, TimeUnit.SECONDS);

        l1Cache.put(l1Key, areaFreeCount);
        return areaFreeCount;
    }

    @Override
    public void reserveSpace(Long spaceId, Long userId, String startTime, String endTime) {
        ParkingSpace space = this.getById(spaceId);
        if (space == null) {
            throw new BizException(ErrorCode.SPACE_NOT_FOUND);
        }
        // 预约上限：同一业主进行中预约最多 3 个（防占位滥用）
        long myActive = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getStatus, 0));
        if (myActive >= 3) {
            throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "进行中的预约已达上限（3个），请先取消或等待入场");
        }

        // 时段校验与冲突检查必须先于车位锁定，保证被拒绝的预约请求不产生任何副作用
        java.time.LocalDateTime st = null;
        java.time.LocalDateTime et = null;
        if (startTime != null && endTime != null) {
            java.time.LocalDateTime s;
            java.time.LocalDateTime e;
            try {
                s = java.time.LocalDateTime.parse(startTime.replace(" ", "T"));
                e = java.time.LocalDateTime.parse(endTime.replace(" ", "T"));
            } catch (Exception ex) {
                throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "预约时间格式不正确");
            }
            if (!e.isAfter(s)) {
                throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "结束时间必须晚于开始时间");
            }
            // E2E 报告 P1-4：时长上限与"不得早于当前"此前只在前端校验，此处下沉到后端与前端同源。
            // 上限配置化（默认 2 小时），前后端按同一把尺拦截；恰好等于上限放行，超过才拒绝。
            if (java.time.Duration.between(s, e).toMinutes() > reserveMaxHours * 60L) {
                throw new BizException(ErrorCode.SPACE_UNAVAILABLE,
                        "预约时长不能超过 " + reserveMaxHours + " 小时");
            }
            if (s.isBefore(java.time.LocalDateTime.now())) {
                throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "预约开始时间不得早于当前时间");
            }
            // 时段冲突：同一车位存在进行中预约且时段重叠 → 拒绝
            java.util.List<Reservation> conflicts = reservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSpaceId, spaceId)
                            .eq(Reservation::getStatus, 0)
                            .and(w -> w.lt(Reservation::getStartTime, e).gt(Reservation::getEndTime, s)));
            if (!conflicts.isEmpty()) {
                throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "该时段已被他人预约");
            }
            st = s;
            et = e;
        }

        // 原子条件更新：只有空闲(status=0)才能预约，避免并发超卖（最终防线）
        int affected = this.baseMapper.lockToReserved(spaceId, userId);
        if (affected == 0) {
            throw new BizException(ErrorCode.SPACE_UNAVAILABLE);
        }

        // 同步内存对象，供后续 Redis 更新使用
        space.setStatus(Constants.SPACE_STATUS_RESERVED);
        space.setOwnerId(userId);
        // 更新Redis缓存
        updateRedisSpaceStatus(space);

        // 保存预约记录（时段锁）
        if (st != null && et != null) {
            Reservation r = new Reservation();
            r.setSpaceId(spaceId);
            r.setUserId(userId);
            r.setStartTime(st);
            r.setEndTime(et);
            r.setStatus(0);
            reservationMapper.insert(r);
        }
    }

    /**
     * 更新Redis中的车位状态
     */
    private void updateRedisSpaceStatus(ParkingSpace space) {        String key = Constants.REDIS_PARKING_SPACE_STATUS + space.getArea();
        redisTemplate.opsForHash().put(key, String.valueOf(space.getId()), space.getStatus());
        redisTemplate.expire(key, 30, TimeUnit.SECONDS);

        // 失效 L1 统计缓存（含小区维度）
        l1Cache.invalidate(L1_STATS_KEY);
        if (space.getCommunityId() != null) {
            l1Cache.invalidate(L1_STATS_KEY + ":" + space.getCommunityId());
        }
        // 同步更新可用数量统计
        updateAreaFreeCount(space.getArea(), space.getCommunityId());
    }

    /**
     * 更新区域可用车位数（按小区分键）
     */
    private void updateAreaFreeCount(String area, Long communityId) {
        long freeCount = this.count(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getArea, area)
                .eq(communityId != null, ParkingSpace::getCommunityId, communityId)
                .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_FREE));

        String statsKey = communityId == null ? Constants.REDIS_PARKING_AVAILABLE
                : Constants.REDIS_PARKING_AVAILABLE + ":" + communityId;
        redisTemplate.opsForHash().put(statsKey, area, freeCount);
        redisTemplate.expire(statsKey, 30, TimeUnit.SECONDS);
    }

    @Override
    public boolean claimSpace(Long spaceId) {
        int affected = this.baseMapper.lockToOccupied(spaceId);
        if (affected > 0) {
            ParkingSpace s = this.getById(spaceId);
            if (s != null) {
                s.setStatus(Constants.SPACE_STATUS_OCCUPIED);
                updateRedisSpaceStatus(s);
            }
            return true;
        }
        return false;
    }

    @Override
    public void toggleShare(Long spaceId, Integer isShared) {
        ParkingSpace space = this.getById(spaceId);
        if (space == null) throw new RuntimeException("车位不存在");
        space.setIsShared(isShared);
        this.updateById(space);
    }

    @Override
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (r.getStatus() != 0) {
            throw new BizException(ErrorCode.RESERVATION_ALREADY_FINISHED);
        }
        // 释放车位
        this.update(new LambdaUpdateWrapper<ParkingSpace>()
                .eq(ParkingSpace::getId, r.getSpaceId())
                .eq(ParkingSpace::getStatus, Constants.SPACE_STATUS_RESERVED)
                .set(ParkingSpace::getStatus, Constants.SPACE_STATUS_FREE)
                .set(ParkingSpace::getOwnerId, null));
        // 更新Redis
        ParkingSpace s = this.getById(r.getSpaceId());
        if (s != null) {
            s.setStatus(Constants.SPACE_STATUS_FREE);
            s.setOwnerId(null);
            updateRedisSpaceStatus(s);
        }
        // 更新预约状态
        r.setStatus(3);
        reservationMapper.updateById(r);
    }

    @Override
    public java.util.List<Reservation> myReservations(Long userId) {
        return reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime));
    }
}
