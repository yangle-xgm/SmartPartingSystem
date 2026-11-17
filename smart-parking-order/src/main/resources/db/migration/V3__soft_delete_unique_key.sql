-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_order_no 不含 is_deleted，作废后同一订单号无法重用（补偿/重放场景会撞键）。
-- 方案：存储生成列 alive_order_no（未删除时等于 order_no，删除后为 NULL）+ 唯一索引。
-- 幂等：已手工执行过的环境再次运行不会报错。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'parking_order' AND column_name = 'alive_order_no');
SET @sql := IF(@c = 0,
    'ALTER TABLE `parking_order` ADD COLUMN `alive_order_no` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, order_no, NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_order' AND index_name = 'uk_order_no_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `parking_order` ADD UNIQUE KEY `uk_order_no_alive` (`alive_order_no`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_order' AND index_name = 'uk_order_no');
SET @sql := IF(@c > 0, 'ALTER TABLE `parking_order` DROP INDEX `uk_order_no`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
