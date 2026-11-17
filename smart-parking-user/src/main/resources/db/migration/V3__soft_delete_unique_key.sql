-- ============================================================
-- V3：软删除记录不再占用唯一索引（前端测试 D4 的根治方案）
--
-- 问题：uk_username 不含 is_deleted，注销后的用户名永久不可用。
-- 方案：存储生成列 alive_username（未删除时等于 username，删除后为 NULL）+ 唯一索引。
-- 幂等：已手工执行过的环境再次运行不会报错。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'alive_username');
SET @sql := IF(@c = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `alive_username` VARCHAR(191) GENERATED ALWAYS AS (IF(is_deleted = 0, username, NULL)) STORED COMMENT ''仅未删除行有值（D4）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_username_alive');
SET @sql := IF(@c = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_username_alive` (`alive_username`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_username');
SET @sql := IF(@c > 0, 'ALTER TABLE `sys_user` DROP INDEX `uk_username`', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
