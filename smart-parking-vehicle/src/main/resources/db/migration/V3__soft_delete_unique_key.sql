-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_plate_no 不含 is_deleted，解绑后旧行仍占着车牌号，同一车牌无法再次绑定。
-- 方案：存储生成列 alive_plate_no（未删除时等于 plate_no，删除后为 NULL）+ 唯一索引。
-- 幂等：已手工执行过的环境再次运行不会报错。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'vehicle' AND column_name = 'alive_plate_no');
SET @sql := IF(@c = 0,
    'ALTER TABLE `vehicle` ADD COLUMN `alive_plate_no` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, plate_no, NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'vehicle' AND index_name = 'uk_plate_no_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `vehicle` ADD UNIQUE KEY `uk_plate_no_alive` (`alive_plate_no`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'vehicle' AND index_name = 'uk_plate_no');
SET @sql := IF(@c > 0, 'ALTER TABLE `vehicle` DROP INDEX `uk_plate_no`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
