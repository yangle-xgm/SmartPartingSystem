package com.smartparking.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.notify.po.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
