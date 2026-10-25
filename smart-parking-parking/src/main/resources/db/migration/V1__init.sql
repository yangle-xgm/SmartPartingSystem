-- ============================================================
-- 模块 smart-parking-parking 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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

ALTER TABLE parking_space ADD COLUMN community_id BIGINT DEFAULT 1;

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
