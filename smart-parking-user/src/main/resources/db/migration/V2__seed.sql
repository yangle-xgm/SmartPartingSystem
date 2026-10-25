-- ============================================================
-- 模块 smart-parking-user 种子数据（幂等 INSERT IGNORE）
-- ============================================================

INSERT IGNORE INTO `sys_user` (`id`, `username`, `password`, `phone`, `real_name`, `role`, `status`, `community_id`) VALUES
(1, 'admin', '$2a$10$0192023a7bbd73250516f069df18b500', 'fHORmEiUMGtZoPpgfFnk9g==', '系统管理员', 2, 1, 1),
(1001, 'zhangsan', '$2a$10$6ad14ba9986e3615423dfca256d04e3f', '9Ed+h/IiJJ/fQyGt0UjMGQ==', '张三', 0, 1, 1),
(1002, 'lisi', '$2a$10$6ad14ba9986e3615423dfca256d04e3f', 'mukjcfBIbFYVMn5TlRjZKA==', '李四', 0, 1, 1),
(1003, 'wangwu', '$2a$10$6ad14ba9986e3615423dfca256d04e3f', '/Y05KfYaYqsBZ5idHSFlNw==', '王五', 0, 1, 1),
(2001, 'property_mgr', '$2a$10$0192023a7bbd73250516f069df18b500', 'xmtATzBp9QHMig7tYlzaMg==', '物业管理员', 1, 1, 1);

INSERT IGNORE INTO `audit_log` (`id`, `user_id`, `username`, `operation`, `method`, `params`, `is_success`, `ip`, `cost_time`, `create_time`) VALUES
(2101600000000000001, 1, 'admin', '查看车位列表', 'GET /api/parking/spaces', '{"current":1,"size":10}', 1, '127.0.0.1', 15, '2026-09-18 09:10:00'),
(2101600000000000002, 2001, 'property_mgr', '发布通知', 'POST /api/notify/publish', '{"title":"系统公告","type":4}', 1, '127.0.0.1', 42, '2026-09-18 09:00:00'),
(2101600000000000003, 1001, 'zhangsan', '预约车位', 'POST /api/parking/reserve', '{"spaceId":5,"userId":1001}', 1, '127.0.0.1', 18, '2026-09-19 20:12:00'),
(2101600000000000004, 1001, 'zhangsan', '提交反馈', 'POST /api/notify/feedback', '{"type":1,"title":"地库灯光太暗"}', 1, '127.0.0.1', 25, '2026-09-18 21:05:00'),
(2101600000000000005, 2001, 'property_mgr', '设备联动入场', 'POST /api/order/internal/device-entry', '{"plateNo":"湘A2T817"}', 1, '10.0.0.18', 88, '2026-09-14 08:30:00'),
(2101600000000000006, 1002, 'lisi', '确认支付', 'POST /api/payment/pay', '{"orderNo":"ORD202609180002"}', 1, '127.0.0.1', 66, '2026-09-18 16:10:00'),
(2101600000000000007, NULL, 'zhangsan', '登录', 'POST /api/user/login', '{"username":"zhangsan"}', 0, '127.0.0.1', 9, '2026-09-19 08:30:00'),
(2101600000000000008, 1, 'admin', '查看统计', 'GET /api/stats/overview', '{}', 1, '127.0.0.1', 21, '2026-09-20 09:30:00');

INSERT IGNORE INTO `community` (`id`, `name`, `address`, `city`, `spaces_total`) VALUES
(1, '智慧星河花园', '长沙市岳麓区xx路1号', '长沙', 200),
(2, '滨江国际社区', '长沙市天心区xx路2号', '长沙', 150),
(3, '阳光100', '长沙市雨花区xx路3号', '长沙', 100);

INSERT IGNORE INTO `feedback` (`id`, `user_id`, `user_name`, `type`, `title`, `content`, `status`, `reply`, `reply_time`) VALUES
(1, 1001, '张三', 1, '地库灯光太暗', 'B区B1层灯光太暗，晚上停车不方便，希望尽快处理', 0, NULL, NULL),
(2, 1002, '李四', 2, '建议增加新能源充电桩', 'B区新能源车位较多，建议增设充电桩', 2, '已收到您的建议，已纳入下一批改造计划', '2026-09-18 10:30:00'),
(3, 1003, '王五', 3, '月租如何办理', '请问固定车位月租怎么办理，需要什么材料？', 1, NULL, NULL),
(4, 1001, '张三', 4, 'B区出口道闸故障', 'B区出口道闸今天上午多次无法抬杆，请尽快维修', 2, '已安排维修人员处理，给您带来不便敬请谅解', '2026-09-20 09:15:00');
