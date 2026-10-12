package com.smartparking.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.order.po.ParkingOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 停车订单Mapper
 */
@Mapper
public interface ParkingOrderMapper extends BaseMapper<ParkingOrder> {
}
