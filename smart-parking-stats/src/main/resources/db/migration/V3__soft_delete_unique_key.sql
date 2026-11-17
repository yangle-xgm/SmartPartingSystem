-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_date_area 不含 is_deleted，重算某日某区域统计时无法重建记录。
-- 方案：存储生成列 alive_date_area（未删除时为 "日期|区域"，删除后为 NULL）+ 唯一索引。
-- 幂等：已手工执行过的环境再次运行不会报错。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'parking_stats' AND column_name = 'alive_date_area');
SET @sql := IF(@c = 0,
    'ALTER TABLE `parking_stats` ADD COLUMN `alive_date_area` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, CONCAT(stat_date, ''|'', area), NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_stats' AND index_name = 'uk_date_area_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `parking_stats` ADD UNIQUE KEY `uk_date_area_alive` (`alive_date_area`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_stats' AND index_name = 'uk_date_area');
SET @sql := IF(@c > 0, 'ALTER TABLE `parking_stats` DROP INDEX `uk_date_area`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
