package com.smartparking.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.parking.po.ParkingSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 车位Mapper
 */
@Mapper
public interface ParkingSpaceMapper extends BaseMapper<ParkingSpace> {

    /**
     * 统计各区域车位状态
     */
    List<Map<String, Object>> selectAreaStatusStatistics();

    /**
     * 原子地把空闲车位置为已预约（防并发超卖）。
     * 只有 status=0(空闲) 时才更新成功，返回影响行数。
     */
    @Update("UPDATE parking_space SET status=2, owner_id=#{userId}, update_time=NOW() " +
            "WHERE id=#{id} AND status=0 AND is_deleted=0")
    int lockToReserved(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 原子地把空闲/已预约车位置为已占用（入场防并发一车多位）。
     * 只有 status=0(空闲) 或 2(已预约) 时才更新成功。
     */
    @Update("UPDATE parking_space SET status=1, update_time=NOW() " +
            "WHERE id=#{id} AND status IN (0,2) AND is_deleted=0")
    int lockToOccupied(@Param("id") Long id);
}
