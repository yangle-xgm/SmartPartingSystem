package com.smartparking.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.vehicle.po.MonthlyCardOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 月卡订单 Mapper
 */
@Mapper
public interface MonthlyCardOrderMapper extends BaseMapper<MonthlyCardOrder> {
}
