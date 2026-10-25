-- ============================================================
-- 模块 smart-parking-order 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
