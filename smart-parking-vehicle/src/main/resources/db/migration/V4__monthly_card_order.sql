-- ============================================================
-- V4：月卡订单表
--
-- 背景：原有 /api/vehicle/{id}/renew-monthly 只是把 monthly_expire 加一个月，
-- 不产生任何金额与流水，业主端也无法看到"充了多久、花了多少钱"。
-- 本表承载月卡充值的下单、支付、生效全过程，与停车订单（parking_order）分离：
-- 停车订单是按时计费的消费单，月卡订单是预付费的套餐单，两者计费模型不同。
--
-- 设计要点：
--   * unit_price 下单时快照，后续 billing_rule 调价不影响历史订单金额；
--   * expire_before / expire_after 记录支付前后的有效期，便于对账与客诉追溯；
--   * 不建 vehicle 外键：车辆解绑后月卡订单需作为财务凭证保留。
-- ============================================================

CREATE TABLE `monthly_card_order` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键',
  `order_no` VARCHAR(32) NOT NULL COMMENT '月卡订单号（MC+yyyyMMddHHmmss+6位随机）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '业主ID',
  `vehicle_id` BIGINT UNSIGNED NOT NULL COMMENT '车辆ID',
  `plate_no` VARCHAR(128) NOT NULL COMMENT '车牌（AES 密文，与 vehicle.plate_no 同源）',
  `months` TINYINT UNSIGNED NOT NULL COMMENT '购买月数（1/3/6/12）',
  `unit_price` DECIMAL(10,2) UNSIGNED NOT NULL COMMENT '下单时月单价快照',
  `amount` DECIMAL(10,2) UNSIGNED NOT NULL COMMENT '订单金额 = unit_price * months',
  `pay_type` TINYINT UNSIGNED DEFAULT NULL COMMENT '支付方式（1微信 2支付宝）',
  `pay_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '支付状态（0待支付 1成功 2失败 3已取消）',
  `pay_no` VARCHAR(32) DEFAULT NULL COMMENT '支付流水号（模拟支付）',
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '第三方交易号（模拟）',
  `expire_before` DATETIME DEFAULT NULL COMMENT '支付前月卡到期时间',
  `expire_after` DATETIME DEFAULT NULL COMMENT '支付后月卡到期时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除（0未删除 1已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_monthly_order_no` (`order_no`),
  KEY `idx_monthly_user` (`user_id`),
  KEY `idx_monthly_vehicle` (`vehicle_id`),
  KEY `idx_monthly_pay_no` (`pay_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月卡订单表';
