package com.smartparking.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.notify.mapper.NotificationMapper;
import com.smartparking.notify.po.Notification;
import com.smartparking.notify.query.NotificationQuery;
import com.smartparking.notify.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知服务实现
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    @Override
    public void sendNotification(Long userId, String title, String content, Integer type) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(userId == 1L ? 1 : 0); // admin发的广播对admin自己标记为已读
        notification.setCreateTime(java.time.LocalDateTime.now());
        notification.setUpdateTime(java.time.LocalDateTime.now());

        this.save(notification);
        log.info("发送通知成功：userId={}, title={}", userId, title);
    }

    @Override
    public Page<Notification> pageList(Page<Notification> page, NotificationQuery query) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            // 业主看自己的通知 + 全体广播(userId=1)
            wrapper.and(w -> w.eq(Notification::getUserId, query.getUserId())
                              .or().eq(Notification::getUserId, 1L));
        }
        wrapper.eq(query.getType() != null, Notification::getType, query.getType())
                .orderByDesc(Notification::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public void markAsRead(Long id) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllAsRead(Long userId) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public long getUnreadCount(Long userId) {
        return this.count(new LambdaQueryWrapper<Notification>()
                .and(w -> w.eq(Notification::getUserId, userId)
                          .or().eq(Notification::getUserId, 1L))
                .eq(Notification::getIsRead, 0));
    }
}
