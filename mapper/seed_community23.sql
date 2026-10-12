-- ============================================================
-- 小区 2/3 数据（滨江国际社区 / 阳光100）
-- 幂等脚本，可重复执行；主键固定：用户 1004-1006/2002-2003、车辆 50-52、车位 100-139
-- 密码格式：$2a$10$ + MD5（与 SysUserServiceImpl.passwordEncode 一致）
--   物业 admin123 → $2a$10$0192023a7bbd73250516f069df18b500
--   业主 user123  → $2a$10$6ad14ba9986e3615423dfca256d04e3f
-- 手机号/车牌为 AES-256-ECB 密文（AesUtils 固定密钥）
-- ============================================================
USE smart_parking;

-- ========== 1. 用户 ==========
INSERT IGNORE INTO sys_user (id, username, password, real_name, phone, role, community_id, status) VALUES
(1004, 'zhaoliu',      '$2a$10$6ad14ba9986e3615423dfca256d04e3f', '赵六', 'H5bJxTtZ3WnBwY5Zjq2xVA==', 0, 2, 1),
(1005, 'sunqi',        '$2a$10$6ad14ba9986e3615423dfca256d04e3f', '孙七', 'iAZh0CTgBv04akPR9QvEhg==', 0, 3, 1),
(1006, 'zhouba',       '$2a$10$6ad14ba9986e3615423dfca256d04e3f', '周八', 'PDyZEvVX/h1eShsAVygQDQ==', 0, 2, 1),
(2002, 'property_mgr2', '$2a$10$0192023a7bbd73250516f069df18b500', '滨江物业', '1j7oepqLH74yR4H5pw+xbg==', 1, 2, 1),
(2003, 'property_mgr3', '$2a$10$0192023a7bbd73250516f069df18b500', '阳光物业', '78WgcmAwe27B7WFw45ES9Q==', 1, 3, 1);

-- ========== 2. 车辆（50-52，plate_no 为 AES 密文） ==========
INSERT IGNORE INTO vehicle (id, plate_no, plate_color, owner_id, brand, color, type, status) VALUES
(50, 'VsuQrPh5TIEmli+L567cHA==', '蓝', 1004, '大众',   '白色', 0, 0), -- 湘B88888
(51, 'SnRhiowhutKlRz04sFdXXQ==', '蓝', 1006, '丰田',   '黑色', 0, 0), -- 湘B77777
(52, 'PF3CryTstNMweBmnXEGyGw==', '蓝', 1005, '比亚迪', '白色', 0, 0); -- 湘C66666

-- ========== 3. 车位：小区2（滨江国际社区）C区 C-401~C-420（id 100-119） ==========
INSERT IGNORE INTO parking_space (id, space_no, area, floor, type, status, owner_id, month_price, is_shared, community_id) VALUES
(100, 'C-401', 'C', 'B1', 1, 1, 1004, 300.00, 1, 2), -- 赵六固定位（月租车占用）
(101, 'C-402', 'C', 'B1', 1, 1, 1004, 300.00, 1, 2),
(102, 'C-403', 'C', 'B1', 1, 1, 1006, 300.00, 1, 2), -- 周八固定位（月租车占用）
(103, 'C-404', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(104, 'C-405', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(105, 'C-406', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(106, 'C-407', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(107, 'C-408', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(108, 'C-409', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(109, 'C-410', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(110, 'C-411', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(111, 'C-412', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(112, 'C-413', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(113, 'C-414', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(114, 'C-415', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(115, 'C-416', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(116, 'C-417', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(117, 'C-418', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(118, 'C-419', 'C', 'B1', 0, 0, NULL, NULL, 0, 2),
(119, 'C-420', 'C', 'B1', 0, 0, NULL, NULL, 0, 2);

-- ========== 4. 车位：小区3（阳光100）D区 D-401~D-420（id 120-139） ==========
INSERT IGNORE INTO parking_space (id, space_no, area, floor, type, status, owner_id, month_price, is_shared, community_id) VALUES
(120, 'D-401', 'D', 'B1', 1, 1, 1005, 300.00, 1, 3), -- 孙七固定位（月租车占用）
(121, 'D-402', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(122, 'D-403', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(123, 'D-404', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(124, 'D-405', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(125, 'D-406', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(126, 'D-407', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(127, 'D-408', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(128, 'D-409', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(129, 'D-410', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(130, 'D-411', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(131, 'D-412', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(132, 'D-413', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(133, 'D-414', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(134, 'D-415', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(135, 'D-416', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(136, 'D-417', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(137, 'D-418', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(138, 'D-419', 'D', 'B1', 0, 0, NULL, NULL, 0, 3),
(139, 'D-420', 'D', 'B1', 0, 0, NULL, NULL, 0, 3);

-- ========== 5. 小区2/3 计费规则（复用现有 1-4 规则即可，此处仅关联性说明） ==========
-- 规则表为全局配置，各小区共用；如需分小区差异化费率，可另行扩展 billing_rule.community_id
