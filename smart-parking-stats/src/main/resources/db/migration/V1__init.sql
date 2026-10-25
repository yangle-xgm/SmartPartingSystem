-- ============================================================
-- 模块 smart-parking-stats 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
