package com.smartparking.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.device.po.Device;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
