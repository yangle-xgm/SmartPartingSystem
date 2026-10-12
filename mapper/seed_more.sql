-- ============================================================
-- 小区智慧停车管理系统 - 数据补充/修复脚本（幂等，可重复执行）
-- 覆盖：车辆(1-10) / 计费规则 / 设备 / 订单(100-110) / 支付 / 通知 / 统计 / 访客白名单 / 反馈 / 分润 / 对账 / 预约 / 审计
-- ============================================================
USE smart_parking;

-- ========== 1. 车辆（1-10，含车主自有车辆，plate_no 为 AES 密文） ==========
INSERT IGNORE INTO vehicle (id, plate_no, plate_color, owner_id, brand, color, type, status) VALUES
(1,  'eRUL3vv8T+GZsrMT+E5ENQ==', '蓝', 1001, '大众',   '白色', 0, 0), -- 湘A9K527
(2,  'aUOOUp195yHJyM77LwBZwg==', '绿', 1002, '比亚迪', '黑色', 0, 0), -- 湘A7Q183
(3,  'AAwsMgALTRFtQICtl0hKrg==', '蓝', 1003, '丰田',   '银色', 0, 0), -- 湘A3P664
(4,  'oO+bcEhLABc2w2+I8R6C4w==', '蓝', 1001, '本田',   '红色', 0, 0), -- 湘A5R946
(5,  'YciTjwKuLP86xnjX3Op45Q==', '绿', 1002, '特斯拉', '白色', 0, 0), -- 湘A2T817
(6,  'ghVAJxwwr+X4Wx+MDu1WXw==', '蓝', 1003, '大众',   '黑色', 0, 0), -- 湘A8M335
(7,  'JBCm4yTVoKeL3SmDALr+fg==', '蓝', 1001, '奔驰',   '白色', 0, 0), -- 湘A6N248
(8,  'Kk6/IEVKmzxUHrSfWk6lwg==', '蓝', 1001, '奥迪',   '灰色', 0, 0), -- 湘A1F029
(9,  'VfLxdvMDiJ0kbqGn6CEECA==', '蓝', 1002, '丰田',   '银色', 0, 0), -- 湘A9L362
(10, 'vBST3FkPjEbecxar2yU+Tw==', '绿', 1003, '比亚迪', '蓝色', 0, 0); -- 湘A4D715

-- ========== 2. 计费规则 ==========
INSERT IGNORE INTO billing_rule (id, rule_name, vehicle_type, plate_color, free_minutes, unit_price, unit_duration, daily_cap, monthly_price, weekend_unit_price, weekend_daily_cap, status) VALUES
(1, '临时车标准计费',   1, NULL, 30, 2.50, 30, 50.00, NULL, 3.00, 60.00, 1),
(2, '新能源车优惠计费', 1, '绿', 30, 2.00, 30, 40.00, NULL, 2.50, 50.00, 1),
(3, '月租车计费',       2, NULL, 0,  0.00, 60, NULL,  300.00, NULL, NULL, 1),
(4, '访客车计费',       0, NULL, 15, 3.00, 30, 60.00, NULL, 3.50, 70.00, 1);

-- ========== 3. 设备 ==========
INSERT IGNORE INTO device (id, device_no, device_name, device_type, location, mqtt_topic, status, last_heartbeat) VALUES
(1,  'CAM-A-001', 'A区入口摄像头', 2, 'A区入口', 'device/camera/CAM-A-001/plate', 1, NOW()),
(2,  'CAM-A-002', 'A区出口摄像头', 2, 'A区出口', 'device/camera/CAM-A-002/plate', 1, NOW()),
(3,  'GATE-A-001', 'A区入口道闸', 1, 'A区入口', 'device/gate/GATE-A-001/control', 1, NOW()),
(4,  'GATE-A-002', 'A区出口道闸', 1, 'A区出口', 'device/gate/GATE-A-002/control', 1, NOW()),
(5,  'CAM-B-001', 'B区入口摄像头', 2, 'B区入口', 'device/camera/CAM-B-001/plate', 1, NOW()),
(6,  'GATE-B-001', 'B区入口道闸', 1, 'B区入口', 'device/gate/GATE-B-001/control', 1, NOW()),
(7,  'MAG-B-001', 'B区地磁传感器B1-01', 3, 'B区B1-01', NULL, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8,  'DISP-A-001', 'A区入口显示屏', 4, 'A区入口', 'device/disp/DISP-A-001/control', 1, NOW()),
(9,  'CAM-C-001', 'C区入口摄像头', 2, 'C区入口', 'device/camera/CAM-C-001/plate', 1, NOW()),
(10, 'GATE-C-001', 'C区入口道闸', 1, 'C区入口', 'device/gate/GATE-C-001/control', 1, NOW()),
(11, 'GATE-B-002', 'B区出口道闸', 1, 'B区出口', 'device/gate/GATE-B-002/control', 1, NOW()),
(12, 'CAM-B-002', 'B区出口摄像头', 2, 'B区出口', 'device/camera/CAM-B-002/plate', 1, NOW()),
(13, 'MAG-C-001', 'C区地磁传感器C1-01', 3, 'C区B2-01', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(14, 'DISP-B-001', 'B区入口显示屏', 4, 'B区入口', 'device/disp/DISP-B-001/control', 1, NOW());

-- ========== 4. 订单（进行中/已完成/已退款/已取消） ==========
INSERT IGNORE INTO parking_order (id, order_no, vehicle_id, plate_no, space_id, space_no, entry_time, exit_time, duration, billing_rule_id, amount, pay_status, status) VALUES
(100, 'ORD202609140001', 5, '湘A2T817', 11, 'B-002', '2026-09-20 08:00:00', NULL, NULL, 1, NULL, 0, 0),
(101, 'ORD202609140002', 9, '湘A9L362', 21, 'C-002', '2026-09-20 08:00:00', NULL, NULL, 4, NULL, 0, 0),
(102, 'ORD202609150001', 2, '湘A7Q183', 25, 'A-103', '2026-09-20 08:00:00', NULL, NULL, 2, NULL, 0, 0),
(103, 'ORD202609150002', 6, '湘A8M335', 28, 'A-106', '2026-09-20 08:00:00', NULL, NULL, 1, NULL, 0, 0),
(104, 'ORD202609160001', 1, '湘A9K527', 10, 'B-001', '2026-09-16 08:30:00', '2026-09-16 10:00:00', 90, 1, 5.00, 1, 1),
(105, 'ORD202609160002', 3, '湘A3P664', 12, 'B-003', '2026-09-16 07:15:00', '2026-09-16 20:45:00', 810, 1, 50.00, 1, 1),
(106, 'ORD202609170001', 4, '湘A5R946', 13, 'B-004', '2026-09-17 09:00:00', '2026-09-17 11:30:00', 150, 1, 10.00, 1, 1),
(107, 'ORD202609170002', 7, '湘A6N248', 20, 'C-001', '2026-09-17 14:00:00', '2026-09-17 16:30:00', 150, 4, 15.00, 1, 1),
(108, 'ORD202609180001', 8, '湘A1F029', 14, 'B-005', '2026-09-18 10:00:00', '2026-09-18 12:00:00', 120, 1, 7.50, 1, 1),
(109, 'ORD202609180002', 2, '湘A7Q183', 24, 'A-102', '2026-09-18 15:00:00', '2026-09-18 16:00:00', 60, 2, 2.00, 2, 1),
(110, 'ORD202609190001', 10, '湘A4D715', 22, 'C-003', '2026-09-19 11:00:00', NULL, NULL, 1, NULL, 0, 2);

-- ========== 5. 支付记录 ==========
INSERT IGNORE INTO payment_record (id, pay_no, order_id, user_id, amount, pay_type, pay_status, transaction_id, pay_time) VALUES
(101, 'PAY202609160001', 104, 1001, 5.00,  1, 1, 'WX202609160001', '2026-09-16 10:05:00'),
(102, 'PAY202609160002', 105, 1003, 50.00, 2, 1, 'ZFB202609160001', '2026-09-16 20:50:00'),
(103, 'PAY202609170001', 106, 1001, 10.00, 1, 1, 'WX202609170001', '2026-09-17 11:35:00'),
(104, 'PAY202609170002', 107, 1001, 15.00, 2, 1, 'ZFB202609170001', '2026-09-17 16:35:00'),
(105, 'PAY202609180001', 109, 1002, 2.00,  1, 3, 'WX202609180002', '2026-09-18 16:10:00');

-- ========== 6. 通知 ==========
INSERT IGNORE INTO notification (id, user_id, title, content, type, is_read, create_time) VALUES
(101, 1001, '车辆入场通知', '您的车辆 湘A9K527 已于 2026-09-16 08:30 入场，停放在 B-001', 1, 1, '2026-09-16 08:30:00'),
(102, 1001, '车辆出场通知', '您的车辆 湘A9K527 已出场，停车时长 90 分钟，费用 5.00 元', 2, 0, '2026-09-16 10:00:00'),
(103, 1001, '缴费确认', '您的车辆 湘A1F029 停车费 7.50 元已支付', 3, 0, '2026-09-18 12:05:00'),
(104, 1002, '车辆入场通知', '您的车辆 湘A7Q183 已于 2026-09-18 15:00 入场，停放在 A-102', 1, 0, '2026-09-18 15:00:00'),
(105, 1002, '退款到账通知', '您的订单 ORD202609180002 已退款 2.00 元', 3, 0, '2026-09-18 16:15:00'),
(106, 1003, '车辆出场通知', '您的车辆 湘A3P664 已出场，停车时长 810 分钟，费用 50.00 元', 2, 1, '2026-09-16 20:45:00'),
(107, 1003, '缴费提醒', '您有未支付的停车订单，请及时处理', 3, 0, '2026-09-18 12:05:00'),
(108, 2001, '系统公告', '本月停车费对账将于月底前完成，请物业确认各区域收入数据', 4, 0, '2026-09-18 09:00:00'),
(109, 1001, '系统公告', '小区停车系统升级完成，新增访客车辆白名单自动识别功能', 4, 1, '2026-09-20 10:00:00');

-- ========== 7. 统计数据 ==========
INSERT IGNORE INTO parking_stats (id, stat_date, area, total_spaces, occupied_spaces, entry_count, exit_count, total_income, avg_duration) VALUES
(4,  '2026-09-18', 'A', 17, 8, 45, 38, 320.00, 95),
(5,  '2026-09-18', 'B', 17, 7, 38, 33, 280.00, 110),
(6,  '2026-09-18', 'C', 13, 6, 22, 18, 150.00, 70),
(7,  '2026-09-19', 'A', 17, 7, 41, 40, 295.00, 90),
(8,  '2026-09-19', 'B', 17, 8, 36, 31, 302.00, 120),
(9,  '2026-09-19', 'C', 13, 5, 19, 20, 138.00, 65),
(10, '2026-09-20', 'A', 17, 6, 28, 26, 210.00, 85),
(11, '2026-09-20', 'B', 17, 7, 24, 21, 198.00, 100),
(12, '2026-09-20', 'C', 13, 5, 15, 12, 96.00, 60);

-- ========== 8. 访客白名单 ==========
INSERT IGNORE INTO visitor_invite (id, user_id, plate_no, invite_code, status, expire_time, used_time) VALUES
(1, 1001, 'SRUkLvEwS3jkk/C0KXbEJw==', '582194', 0, '2026-10-01 23:59:59', '2026-09-15 14:20:00'),
(2, 1002, 'NpIocUkehsk/VfdkpNZZiA==', '548273', 0, '2026-09-30 23:59:59', NULL),
(3, 1003, 'YduGks1G5lnRnWLcvFZI6Q==', '913605', 0, '2026-10-05 23:59:59', '2026-09-18 09:40:00'),
(4, 1001, 'ngJMATbiqAyt216oj96Vhg==', '720418', 2, '2026-09-10 23:59:59', '2026-09-08 18:30:00'),
(5, 1002, 'oHv9Q0QudCg6uADt4P8FBg==', '316774', 3, '2026-09-20 23:59:59', NULL);

-- ========== 9. 业主反馈（双向） ==========
INSERT IGNORE INTO feedback (id, user_id, user_name, type, title, content, status, reply, reply_time) VALUES
(1, 1001, '张三', 1, '地库灯光太暗', 'B区B1层灯光太暗，晚上停车不方便，希望尽快处理', 0, NULL, NULL),
(2, 1002, '李四', 2, '建议增加新能源充电桩', 'B区新能源车位较多，建议增设充电桩', 2, '已收到您的建议，已纳入下一批改造计划', '2026-09-18 10:30:00'),
(3, 1003, '王五', 3, '月租如何办理', '请问固定车位月租怎么办理，需要什么材料？', 1, NULL, NULL),
(4, 1001, '张三', 4, 'B区出口道闸故障', 'B区出口道闸今天上午多次无法抬杆，请尽快维修', 2, '已安排维修人员处理，给您带来不便敬请谅解', '2026-09-20 09:15:00');

-- ========== 10. 共享车位分润（固定车位设为共享，他车停放收益 7:3） ==========
UPDATE parking_space SET is_shared=1 WHERE id IN (1, 2);
INSERT IGNORE INTO share_income (id, space_id, owner_id, payment_id, total_amount, owner_ratio, owner_amount, property_amount) VALUES
(1, 1, 1001, 101, 5.00, 0.70, 3.50, 1.50),
(2, 2, 1002, 103, 10.00, 0.70, 7.00, 3.00);

-- ========== 11. 支付对账 ==========
INSERT IGNORE INTO payment_reconciliation (recon_date, pay_count, total_amount, refund_count, refund_amount, diff_amount, status, remark) VALUES
('2026-09-18', 3, 65.00, 0, 0.00, 0.00, 0, '对账正常'),
('2026-09-19', 2, 17.00, 1, 2.00, 0.00, 0, '含一笔退款'),
('2026-09-20', 1, 2.00, 0, 0.00, 0.00, 0, '对账正常');

-- ========== 12. 车位预约（指定id保证幂等；清理重复后插入） ==========
DELETE r1 FROM reservation r1 JOIN reservation r2
  ON r1.space_id=r2.space_id AND r1.user_id=r2.user_id AND r1.start_time=r2.start_time AND r1.id>r2.id;
INSERT IGNORE INTO reservation (id, space_id, user_id, start_time, end_time, status) VALUES
(100, 5,  1001, '2026-09-21 19:00:00', '2026-09-21 21:00:00', 0),
(101, 26, 1003, '2026-09-22 10:00:00', '2026-09-22 11:30:00', 0);
UPDATE parking_space SET status=2, owner_id=1001 WHERE id=5 AND status=0;
UPDATE parking_space SET status=2, owner_id=1003 WHERE id=26 AND status=0;

-- ========== 13. 审计日志 ==========
INSERT IGNORE INTO audit_log (id, user_id, username, operation, method, params, is_success, ip, cost_time, create_time) VALUES
(2101600000000000001, 1, 'admin', '查看车位列表', 'GET /api/parking/spaces', '{"current":1,"size":10}', 1, '127.0.0.1', 15, '2026-09-18 09:10:00'),
(2101600000000000002, 2001, 'property_mgr', '发布通知', 'POST /api/notify/publish', '{"title":"系统公告","type":4}', 1, '127.0.0.1', 42, '2026-09-18 09:00:00'),
(2101600000000000003, 1001, 'zhangsan', '预约车位', 'POST /api/parking/reserve', '{"spaceId":5,"userId":1001}', 1, '127.0.0.1', 18, '2026-09-19 20:12:00'),
(2101600000000000004, 1001, 'zhangsan', '提交反馈', 'POST /api/notify/feedback', '{"type":1,"title":"地库灯光太暗"}', 1, '127.0.0.1', 25, '2026-09-18 21:05:00'),
(2101600000000000005, 2001, 'property_mgr', '设备联动入场', 'POST /api/order/internal/device-entry', '{"plateNo":"湘A2T817"}', 1, '10.0.0.18', 88, '2026-09-14 08:30:00'),
(2101600000000000006, 1002, 'lisi', '确认支付', 'POST /api/payment/pay', '{"orderNo":"ORD202609180002"}', 1, '127.0.0.1', 66, '2026-09-18 16:10:00'),
(2101600000000000007, NULL, 'zhangsan', '登录', 'POST /api/user/login', '{"username":"zhangsan"}', 0, '127.0.0.1', 9, '2026-09-19 08:30:00'),
(2101600000000000008, 1, 'admin', '查看统计', 'GET /api/stats/overview', '{}', 1, '127.0.0.1', 21, '2026-09-20 09:30:00');