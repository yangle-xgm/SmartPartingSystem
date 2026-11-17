-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_device_no 不含 is_deleted，拆机后同一设备编号无法重新注册。
-- 方案：存储生成列 alive_device_no（未删除时等于 device_no，删除后为 NULL）+ 唯一索引。
-- 幂等：已手工执行过的环境再次运行不会报错。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'device' AND column_name = 'alive_device_no');
SET @sql := IF(@c = 0,
    'ALTER TABLE `device` ADD COLUMN `alive_device_no` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, device_no, NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'device' AND index_name = 'uk_device_no_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `device` ADD UNIQUE KEY `uk_device_no_alive` (`alive_device_no`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'device' AND index_name = 'uk_device_no');
SET @sql := IF(@c > 0, 'ALTER TABLE `device` DROP INDEX `uk_device_no`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
