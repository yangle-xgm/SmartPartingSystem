-- ============================================================
-- 模块 smart-parking-vehicle 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
