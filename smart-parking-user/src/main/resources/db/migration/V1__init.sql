-- ============================================================
-- 模块 smart-parking-user 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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

ALTER TABLE sys_user ADD COLUMN community_id BIGINT DEFAULT 1;

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
