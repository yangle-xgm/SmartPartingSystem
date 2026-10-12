package com.smartparking.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.parking.po.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    @Update("UPDATE reservation SET status=3 WHERE id=#{id} AND status=0")
    int cancelReservation(@Param("id") Long id);
}
