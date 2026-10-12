package com.smartparking.notify.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.notify.po.Notification;
import com.smartparking.notify.query.NotificationQuery;

/**
 * 通知服务接口
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 发送通知
     */
    void sendNotification(Long userId, String title, String content, Integer type);

    /**
     * 分页查询通知列表
     */
    Page<Notification> pageList(Page<Notification> page, NotificationQuery query);

    /**
     * 标记已读
     */
    void markAsRead(Long id);

    /**
     * 全部已读
     */
    void markAllAsRead(Long userId);

    /**
     * 获取未读数量
     */
    long getUnreadCount(Long userId);
}
