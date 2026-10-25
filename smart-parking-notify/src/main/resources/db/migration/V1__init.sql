-- ============================================================
-- 模块 smart-parking-notify 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
