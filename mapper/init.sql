-- ============================================================
-- 小区智慧停车管理系统 - 数据库初始化脚本（单库版）
-- 适用于 MySQL 8.0+
-- 所有微服务共用一个数据库 smart_parking
-- 在 Navicat 中执行此脚本即可完成所有库表创建
-- 命名规范：
--   1. 表达是与否概念的字段使用 is_xxx，类型 unsigned tinyint
--   2. 非负数字段一律使用 unsigned
--   3. 表名、字段名使用小写字母或数字
--   4. 表名不使用复数名词
-- 外键说明：
--   表间关系使用物理外键约束（fk_子表_父表 命名），保证参照完整性；
--   应用层使用逻辑删除（is_deleted），删除操作为 UPDATE 不会触发外键限制
-- ============================================================

-- 关闭外键检查，保证脚本可重复执行（DROP 父表/子表顺序无关）
SET FOREIGN_KEY_CHECKS = 0;

-- 创建统一数据库
CREATE DATABASE IF NOT EXISTS `smart_parking` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `smart_parking`;

-- ============================================================
-- 1. 用户表
-- ============================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键，雪花算法生成',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `phone` VARCHAR(128) DEFAULT NULL COMMENT '手机号（AES加密存储）',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `role` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色（0业主 1管理员 2超级管理员）',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态（0禁用 1正常）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ============================================================
-- 2. 车位表
-- ============================================================
DROP TABLE IF EXISTS `parking_space`;
CREATE TABLE `parking_space` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `space_no` VARCHAR(20) NOT NULL COMMENT '车位编号',
  `area` VARCHAR(50) NOT NULL COMMENT '所属区域（A区/B区等）',
  `floor` VARCHAR(10) DEFAULT NULL COMMENT '楼层/层数',
  `type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类型（0固定车位 1临时车位 2访客车位）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态（0空闲 1已占用 2预约中 3维护中）',
  `owner_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '业主ID（固定车位）',
  `month_price` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '月租价格',
  `is_shared` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否共享（0不共享 1共享）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_no` (`space_no`),
  KEY `idx_status_area` (`status`, `area`),
  KEY `idx_owner_id` (`owner_id`),
  CONSTRAINT `fk_space_owner` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车位表';

-- ============================================================
-- 3. 车辆表
-- ============================================================
DROP TABLE IF EXISTS `vehicle`;
CREATE TABLE `vehicle` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `plate_no` VARCHAR(128) NOT NULL COMMENT '车牌号（AES加密存储）',
  `plate_color` VARCHAR(10) DEFAULT '蓝' COMMENT '车牌颜色（蓝/绿/黄）',
  `owner_id` BIGINT UNSIGNED NOT NULL COMMENT '车主ID',
  `brand` VARCHAR(50) DEFAULT NULL COMMENT '车辆品牌',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '车身颜色',
  `type` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '类型（0私家车 1访客车辆）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态（0正常 1黑名单）',
  `monthly_expire` DATETIME DEFAULT NULL COMMENT '月卡到期时间（NULL非月卡）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`),
  KEY `idx_owner_id` (`owner_id`),
  CONSTRAINT `fk_vehicle_owner` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆表';

-- ============================================================
-- 4. 计费规则表
-- ============================================================
DROP TABLE IF EXISTS `billing_rule`;
CREATE TABLE `billing_rule` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `rule_name` VARCHAR(50) NOT NULL COMMENT '规则名称',
  `vehicle_type` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '适用车辆类型（0全部 1临时车 2月租车）',
  `plate_color` VARCHAR(10) DEFAULT NULL COMMENT '适用车牌颜色（NULL表示全部）',
  `free_minutes` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
  `unit_price` DECIMAL(10,2) UNSIGNED NOT NULL COMMENT '单位价格（元）',
  `unit_duration` INT UNSIGNED NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
  `daily_cap` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '每日封顶金额',
  `monthly_price` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '月租价格',
  `weekend_unit_price` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '周末单位价格（NULL用unit_price）',
  `weekend_daily_cap` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '周末每日封顶（NULL用daily_cap）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态（0禁用 1启用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计费规则表';

-- ============================================================
-- 5. 停车订单表
-- ============================================================
DROP TABLE IF EXISTS `parking_order`;
CREATE TABLE `parking_order` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `vehicle_id` BIGINT UNSIGNED NOT NULL COMMENT '车辆ID',
  `plate_no` VARCHAR(128) DEFAULT NULL COMMENT '冗余：车牌号（AES加密存储）',
  `space_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '车位ID',
  `space_no` VARCHAR(20) DEFAULT NULL COMMENT '冗余：车位号',
  `entry_time` DATETIME NOT NULL COMMENT '入场时间',
  `exit_time` DATETIME DEFAULT NULL COMMENT '出场时间',
  `duration` INT UNSIGNED DEFAULT NULL COMMENT '停车时长（分钟）',
  `billing_rule_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '计费规则ID',
  `amount` DECIMAL(10,2) UNSIGNED DEFAULT NULL COMMENT '应付金额',
  `pay_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '支付状态（0未支付 1已支付 2已退款）',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单状态（0进行中 1已完成 2已取消）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_plate_no` (`plate_no`),
  KEY `idx_space_id` (`space_id`),
  KEY `idx_billing_rule_id` (`billing_rule_id`),
  KEY `idx_entry_time` (`entry_time`),
  KEY `idx_pay_status_time` (`pay_status`, `entry_time`),
  CONSTRAINT `fk_order_vehicle` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicle` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_order_space` FOREIGN KEY (`space_id`) REFERENCES `parking_space` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_order_billing_rule` FOREIGN KEY (`billing_rule_id`) REFERENCES `billing_rule` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='停车订单表';

-- ============================================================
-- 6. 支付记录表
-- ============================================================
DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `pay_no` VARCHAR(32) NOT NULL COMMENT '支付流水号',
  `order_id` BIGINT UNSIGNED NOT NULL COMMENT '关联订单ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '支付用户ID',
  `amount` DECIMAL(10,2) UNSIGNED NOT NULL COMMENT '支付金额',
  `pay_type` TINYINT UNSIGNED NOT NULL COMMENT '支付方式（1微信 2支付宝 3现金）',
  `pay_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '支付状态（0待支付 1成功 2失败 3退款）',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '第三方交易号',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `parking_order` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付记录表';

-- ============================================================
-- 7. 设备表
-- ============================================================
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `device_no` VARCHAR(50) NOT NULL COMMENT '设备编号',
  `device_name` VARCHAR(100) NOT NULL COMMENT '设备名称',
  `device_type` TINYINT UNSIGNED NOT NULL COMMENT '设备类型（1道闸 2摄像头 3地磁 4显示屏）',
  `location` VARCHAR(100) NOT NULL COMMENT '安装位置',
  `mqtt_topic` VARCHAR(100) DEFAULT NULL COMMENT 'MQTT通信主题',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态（0离线 1在线 2故障）',
  `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最后心跳时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_no` (`device_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备表';

-- ============================================================
-- 8. 通知表
-- ============================================================
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `title` VARCHAR(100) NOT NULL COMMENT '通知标题',
  `content` TEXT COMMENT '通知内容',
  `type` TINYINT UNSIGNED NOT NULL COMMENT '类型（1入场通知 2出场通知 3缴费提醒 4系统公告）',
  `is_read` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已读（0未读 1已读）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  CONSTRAINT `fk_notify_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知表';

-- ============================================================
-- 9. 操作日志表
-- ============================================================
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
  `operation` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法全路径',
  `params` TEXT DEFAULT NULL COMMENT '请求参数（JSON）',
  `is_success` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '操作结果（0失败 1成功）',
  `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址',
  `cost_time` INT UNSIGNED DEFAULT NULL COMMENT '耗时（毫秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';

-- ============================================================
-- 10. 统计数据表
-- ============================================================
DROP TABLE IF EXISTS `parking_stats`;
CREATE TABLE `parking_stats` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `area` VARCHAR(50) DEFAULT NULL COMMENT '区域',
  `total_spaces` INT UNSIGNED DEFAULT 0 COMMENT '总车位数',
  `occupied_spaces` INT UNSIGNED DEFAULT 0 COMMENT '已占用车位数',
  `entry_count` INT UNSIGNED DEFAULT 0 COMMENT '入场车辆数',
  `exit_count` INT UNSIGNED DEFAULT 0 COMMENT '出场车辆数',
  `total_income` DECIMAL(10,2) UNSIGNED DEFAULT 0 COMMENT '总收入',
  `avg_duration` INT UNSIGNED DEFAULT 0 COMMENT '平均停车时长（分钟）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_area` (`stat_date`, `area`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统计数据表';

-- ============================================================
-- 11. 访客邀请码表
-- ============================================================
DROP TABLE IF EXISTS `visitor_invite`;
CREATE TABLE `visitor_invite` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '邀请业主ID',
  `plate_no` VARCHAR(128) DEFAULT NULL COMMENT '访客车牌号（AES加密存储）',
  `invite_code` VARCHAR(6) NOT NULL COMMENT '邀请码（6位）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态（0有效 1已使用 2已过期 3已作废）',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `used_time` DATETIME DEFAULT NULL COMMENT '使用时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_invite_code` (`invite_code`),
  CONSTRAINT `fk_invite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='访客邀请码表';

-- ============================================================
-- 12. 支付对账表
-- ============================================================
DROP TABLE IF EXISTS `payment_reconciliation`;
CREATE TABLE `payment_reconciliation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recon_date` DATE NOT NULL COMMENT '对账日期',
  `pay_count` INT UNSIGNED DEFAULT 0 COMMENT '支付笔数',
  `total_amount` DECIMAL(10,2) UNSIGNED DEFAULT 0 COMMENT '支付总金额',
  `refund_count` INT UNSIGNED DEFAULT 0 COMMENT '退款笔数',
  `refund_amount` DECIMAL(10,2) UNSIGNED DEFAULT 0 COMMENT '退款总金额',
  `diff_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '差额',
  `status` TINYINT UNSIGNED DEFAULT 0 COMMENT '状态（0正常 1有差异）',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_date` (`recon_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付对账表';

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE reservation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status TINYINT DEFAULT 0 COMMENT '0已预约 1已入场 2已过期 3已取消',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_income (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  space_id BIGINT COMMENT '车位ID',
  owner_id BIGINT COMMENT '业主ID',
  payment_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  owner_ratio DECIMAL(3,2) DEFAULT 0.70,
  owner_amount DECIMAL(10,2) NOT NULL,
  property_amount DECIMAL(10,2) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  user_name VARCHAR(64),
  type TINYINT DEFAULT 3 COMMENT '1投诉 2建议 3咨询 4紧急',
  title VARCHAR(128),
  content TEXT,
  status TINYINT DEFAULT 0 COMMENT '0待处理 1处理中 2已回复 3已关闭',
  reply TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  reply_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 多小区表
CREATE TABLE community (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  address VARCHAR(256),
  city VARCHAR(64),
  spaces_total INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 车位与用户关联小区
ALTER TABLE parking_space ADD COLUMN community_id BIGINT DEFAULT 1;
ALTER TABLE sys_user ADD COLUMN community_id BIGINT DEFAULT 1;
