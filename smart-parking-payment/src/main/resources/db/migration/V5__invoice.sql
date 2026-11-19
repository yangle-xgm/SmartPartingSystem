-- ============================================================
-- 电子发票（业主要票 / 物业开票）
--
-- 业务口径：
--   1. 只能对「已支付」的停车订单开票，一单一票（uk_order_id 兜底，防并发重复申请）；
--   2. 个人抬头只需姓名，企业抬头必须带纳税人识别号；
--   3. 状态机：0 待开票 → 1 已开票 → 2 已红冲（红冲后该订单不再重开，需人工处理）。
-- 说明：不提供删除接口，故不做软删复用；is_deleted 保留仅为符合全表规范。
-- ============================================================

CREATE TABLE IF NOT EXISTS `invoice` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `invoice_no` VARCHAR(32) DEFAULT NULL COMMENT '发票号（开票时生成）',
  `order_id` BIGINT UNSIGNED NOT NULL COMMENT '停车订单ID',
  `payment_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '支付流水ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '申请人',
  `title_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '抬头类型（1个人 2企业）',
  `title` VARCHAR(100) NOT NULL COMMENT '发票抬头',
  `tax_no` VARCHAR(30) DEFAULT NULL COMMENT '纳税人识别号（企业必填）',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '接收邮箱',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '开票金额',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态（0待开票 1已开票 2已红冲）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注（红冲原因等）',
  `apply_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `issue_time` DATETIME DEFAULT NULL COMMENT '开票时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_time` (`user_id`, `apply_time`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_invoice_order` FOREIGN KEY (`order_id`) REFERENCES `parking_order` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_invoice_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电子发票申请表';
