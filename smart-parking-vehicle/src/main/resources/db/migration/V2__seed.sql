-- ============================================================
-- 模块 smart-parking-vehicle 种子数据（幂等 INSERT IGNORE）
-- ============================================================

INSERT IGNORE INTO `vehicle` (`id`, `plate_no`, `plate_color`, `owner_id`, `brand`, `color`, `type`, `status`) VALUES
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

-- ============================================================
-- 5. 计费规则
-- ============================================================
INSERT IGNORE INTO `billing_rule` (`id`, `rule_name`, `vehicle_type`, `plate_color`, `free_minutes`, `unit_price`, `unit_duration`, `daily_cap`, `monthly_price`, `weekend_unit_price`, `weekend_daily_cap`, `status`) VALUES
(1, '临时车标准计费',   1, NULL, 30, 2.50, 30, 50.00, NULL, 3.00, 60.00, 1),
(2, '新能源车优惠计费', 1, '绿', 30, 2.00, 30, 40.00, NULL, 2.50, 50.00, 1),
(3, '月租车计费',       2, NULL, 0,  0.00, 60, NULL,  300.00, NULL, NULL, 1),
(4, '访客车计费',       0, NULL, 15, 3.00, 30, 60.00, NULL, 3.50, 70.00, 1);
