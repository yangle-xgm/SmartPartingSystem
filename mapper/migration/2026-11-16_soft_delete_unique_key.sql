-- ============================================================
-- 软删除记录占用唯一索引的修复（前端测试 D4）
--
-- 问题：parking_space.uk_space_no / vehicle.uk_plate_no / sys_user.uk_username /
--       device.uk_device_no / parking_order.uk_order_no / payment_record.uk_pay_no /
--       parking_stats.uk_date_area 都不含 is_deleted，逻辑删除后旧记录仍占着业务键，
--       导致「删掉的车位号 / 解绑的车牌 / 注销的用户名」永久无法重建（接口返回 409）。
--
-- 方案：为每张表增加一个"仅未删除行有值"的生成列（is_deleted=0 时等于业务键，否则 NULL），
--       在其上建唯一索引。MySQL 唯一索引允许多个 NULL，因此：
--         · 未删除行的业务键仍然唯一；
--         · 已删除行不参与唯一约束，可反复删除/重建，不受 (key, is_deleted) 组合
--           方案"第二次删除同编号会撞唯一键"的限制。
--
-- 特性：可重复执行（幂等）。执行后会删除旧的单列唯一索引。
-- 注意：本仓库 Flyway 默认关闭（application.yml: spring.flyway.enabled=false），
--       该脚本需按部署方式执行一次（或开启 Flyway 后放入各服务 db/migration）。
-- ============================================================

USE smart_parking;

DROP PROCEDURE IF EXISTS sp_apply_alive_unique;

DELIMITER //
CREATE PROCEDURE sp_apply_alive_unique(
    IN p_table   VARCHAR(64),
    IN p_column  VARCHAR(64),
    IN p_expr    VARCHAR(255),
    IN p_index   VARCHAR(64),
    IN p_old_idx VARCHAR(64)
)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column,
                          '` VARCHAR(191) GENERATED ALWAYS AS (', p_expr, ') STORED COMMENT ''仅未删除行有值（D4）''');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (`', p_column, '`)');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_old_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_old_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL sp_apply_alive_unique('sys_user',       'alive_username',  'IF(is_deleted = 0, username, NULL)',  'uk_username_alive',  'uk_username');
CALL sp_apply_alive_unique('parking_space',  'alive_space_no',  'IF(is_deleted = 0, space_no, NULL)',  'uk_space_no_alive',  'uk_space_no');
CALL sp_apply_alive_unique('vehicle',        'alive_plate_no',  'IF(is_deleted = 0, plate_no, NULL)',  'uk_plate_no_alive',  'uk_plate_no');
CALL sp_apply_alive_unique('parking_order',  'alive_order_no',  'IF(is_deleted = 0, order_no, NULL)',  'uk_order_no_alive',  'uk_order_no');
CALL sp_apply_alive_unique('payment_record', 'alive_pay_no',    'IF(is_deleted = 0, pay_no, NULL)',    'uk_pay_no_alive',    'uk_pay_no');
CALL sp_apply_alive_unique('device',         'alive_device_no', 'IF(is_deleted = 0, device_no, NULL)', 'uk_device_no_alive', 'uk_device_no');
CALL sp_apply_alive_unique('parking_stats',  'alive_date_area', 'IF(is_deleted = 0, CONCAT(stat_date, ''|'', area), NULL)', 'uk_date_area_alive', 'uk_date_area');

DROP PROCEDURE IF EXISTS sp_apply_alive_unique;

-- 校验：应看到 7 个 *_alive 唯一索引，且旧的单列唯一索引已消失
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND non_unique = 0 AND index_name <> 'PRIMARY'
GROUP BY table_name, index_name
ORDER BY table_name;
