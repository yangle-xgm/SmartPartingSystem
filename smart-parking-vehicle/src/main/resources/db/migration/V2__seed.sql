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

-- 注：billing_rule 归 smart-parking-order 所有，种子已移到该模块 V2__seed.sql。
-- 原实现把规则插在 vehicle 模块，导致「vehicle 必须晚于 order 启动」，而 order 的
-- 外键又依赖 vehicle 表 —— 新库按 Flyway 顺序建表时会直接卡死。

