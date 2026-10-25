-- ============================================================
-- 模块 smart-parking-device 种子数据（幂等 INSERT IGNORE）
-- ============================================================

INSERT IGNORE INTO `device` (`id`, `device_no`, `device_name`, `device_type`, `location`, `mqtt_topic`, `status`, `last_heartbeat`) VALUES
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
