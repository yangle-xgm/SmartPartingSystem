-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_space_no 不含 is_deleted，逻辑删除后旧行仍占着车位编号，
--       导致「删掉的 A-001 无法重建」（接口返回 409）。
-- 方案：新增"仅未删除行有值"的存储生成列 alive_space_no（is_deleted=0 时为
--       space_no，否则 NULL），在其上建唯一索引。MySQL 唯一索引允许多个 NULL，
--       未删除行仍唯一，已删除行不参与约束，可反复删除/重建。
--
-- 幂等：已手工执行过本 DDL 的环境再次运行不会报错（重复执行时走 SELECT 1 空操作）。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'parking_space' AND column_name = 'alive_space_no');
SET @sql := IF(@c = 0,
    'ALTER TABLE `parking_space` ADD COLUMN `alive_space_no` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, space_no, NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_space' AND index_name = 'uk_space_no_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `parking_space` ADD UNIQUE KEY `uk_space_no_alive` (`alive_space_no`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_space' AND index_name = 'uk_space_no');
SET @sql := IF(@c > 0, 'ALTER TABLE `parking_space` DROP INDEX `uk_space_no`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
