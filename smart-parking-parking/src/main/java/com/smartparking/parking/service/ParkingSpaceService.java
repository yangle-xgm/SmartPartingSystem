package com.smartparking.parking.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.query.ParkingSpaceQuery;

import java.util.Map;

/**
 * 车位服务接口
 */
public interface ParkingSpaceService extends IService<ParkingSpace> {

    /**
     * 分页查询车位列表（communityId 非空时按小区过滤）
     */
    Page<ParkingSpace> pageList(Page<ParkingSpace> page, ParkingSpaceQuery query, Long communityId);

    /**
     * 查询可用车位（communityId 非空时按小区过滤）
     */
    java.util.List<ParkingSpace> findAvailableSpaces(String area, Long communityId);

    /**
     * 查询全部车位（含占用/预约/维护，供实时地图展示；communityId 非空时按小区过滤）
     */
    java.util.List<ParkingSpace> findAllSpaces(String area, Long communityId);

    /**
     * 更新车位状态
     */
    void updateStatus(Long spaceId, Integer status);

    /**
     * 统计各区域车位状态（communityId 非空时按小区统计）
     */
    Map<String, Object> getAreaStatusStatistics(Long communityId);

    /**
     * 预约车位
     */
    void reserveSpace(Long spaceId, Long userId, String startTime, String endTime);

    void cancelReservation(Long reservationId, Long userId);

    java.util.List<com.smartparking.parking.po.Reservation> myReservations(Long userId);

    /**
     * 原子占用车位（仅空闲/预约态可占用），返回是否成功
     */
    boolean claimSpace(Long spaceId);

    /**
     * 切换车位共享状态
     */
    void toggleShare(Long spaceId, Integer isShared);
}
