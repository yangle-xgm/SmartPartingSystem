-- ============================================================
-- 模块 smart-parking-payment 表结构（Flyway 版本化迁移）
-- 跨模块物理外键保留；依赖 smart-parking-user 先迁移（无外部 FK 依赖）。
-- ============================================================

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
