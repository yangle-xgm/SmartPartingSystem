-- 支付流水支持多业务类型：停车订单（原）与月卡（新增）
--
-- 背景：月卡此前走 vehicle 模块的自助 mock 通道，不产生 payment_record，
-- 导致收入统计、发票、对账三条链路全部漏掉月卡收入。本次把月卡纳入支付服务统一记账。
--
-- 设计：
--   biz_type = 1 停车订单（biz_id / order_id 均为 parking_order.id）
--   biz_type = 2 月卡    （biz_id = monthly_card_order.id，order_id 为 NULL）
-- order_id 必须允许 NULL：月卡不属于停车订单，若保留 NOT NULL 就无法插入。
-- 外键 fk_payment_order 保留即可 —— MySQL 对 NULL 值不校验外键。
--
-- 幂等：重复执行安全（information_schema 判断后再 ALTER）。

SET @has_biz_type := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_record' AND COLUMN_NAME = 'biz_type'
);
SET @sql := IF(@has_biz_type = 0,
    'ALTER TABLE `payment_record`
        ADD COLUMN `biz_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''业务类型（1停车订单 2月卡）'' AFTER `id`,
        ADD COLUMN `biz_id` BIGINT UNSIGNED NULL COMMENT ''业务单ID（停车=parking_order.id 月卡=monthly_card_order.id）'' AFTER `biz_type`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @order_id_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_record' AND COLUMN_NAME = 'order_id'
);
SET @sql := IF(@order_id_nullable = 'NO',
    'ALTER TABLE `payment_record` MODIFY COLUMN `order_id` BIGINT UNSIGNED NULL COMMENT ''关联停车订单ID（月卡流水为 NULL）''',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_biz_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_record' AND INDEX_NAME = 'idx_biz'
);
SET @sql := IF(@has_biz_index = 0,
    'ALTER TABLE `payment_record` ADD KEY `idx_biz` (`biz_type`, `biz_id`)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史流水全部是停车订单，回填 biz_id 保持一致口径
UPDATE `payment_record` SET `biz_id` = `order_id` WHERE `biz_type` = 1 AND `biz_id` IS NULL;
