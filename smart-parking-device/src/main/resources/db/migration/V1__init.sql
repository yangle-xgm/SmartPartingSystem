-- ============================================================
-- 模块 smart-parking-device 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
