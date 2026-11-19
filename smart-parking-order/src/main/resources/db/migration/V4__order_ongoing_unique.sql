-- ============================================================
-- 报告 P0-8：单车"一笔进行中订单"唯一约束
--
-- 背景：入场是 check-then-act（先 getCurrentOrder 再 insert），设备放行与手动入口
-- 并发时会插两笔 status=0 的订单，导致重复计费；且 getCurrentOrder 用 getOne
-- 遇多行直接抛异常，该车此后查询/入场全 500。
--
-- 方案：沿用本项目 alive_* 生成列模式 —— 仅「未删除 且 进行中」的行才写入 vehicle_id，
-- 其余行为 NULL（MySQL 唯一索引允许多个 NULL），从而由数据库兜住并发。
--
-- 【为何用 VIRTUAL 而不是 STORED】
-- vehicle_id 上存在外键 fk_order_vehicle。MySQL 8 在给「已持有外键约束」的表新增
-- STORED 生成列时，会对表达式引用的外键列做隐式重建，最终报
--   ERROR 1215 (HY000): Cannot add foreign key constraint
-- （已用最小场景复现：只要生成列表达式引用外键列，即使不加唯一键也必失败）。
-- VIRTUAL 生成列不参与行存储、不需要重建数据文件，因此不受该限制；
-- InnoDB 自 5.7 起支持在虚拟生成列上建二级/唯一索引，唯一性语义与 STORED 完全一致。
--
-- 幂等：用 information_schema 判断后动态执行，重复运行不会报错。
-- ============================================================

-- 1. 先清理历史脏数据：同一车辆存在多笔进行中订单时，保留最新一笔，其余置为已取消
--    （必须在建唯一索引前完成，否则加索引会因重复值失败）
UPDATE parking_order o
JOIN (
    SELECT vehicle_id, MAX(id) AS keep_id
    FROM parking_order
    WHERE is_deleted = 0 AND status = 0
    GROUP BY vehicle_id
    HAVING COUNT(*) > 1
) d ON o.vehicle_id = d.vehicle_id AND o.id <> d.keep_id
SET o.status = 2, o.update_time = NOW()
WHERE o.is_deleted = 0 AND o.status = 0;

-- 2. 虚拟生成列：仅「未删除 且 进行中」的行有值，其余为 NULL
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'parking_order'
             AND column_name = 'alive_vehicle_ongoing');
SET @sql := IF(@c = 0,
    'ALTER TABLE `parking_order` ADD COLUMN `alive_vehicle_ongoing` BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(is_deleted = 0 AND status = 0, vehicle_id, NULL)) VIRTUAL
        COMMENT ''仅进行中且未删除的行有值（P0-8：单车一笔进行中订单）''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. 唯一索引
SET @c := (SELECT COUNT(*) FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'parking_order'
             AND index_name = 'uk_vehicle_ongoing');
SET @sql := IF(@c = 0,
    'ALTER TABLE `parking_order` ADD UNIQUE KEY `uk_vehicle_ongoing` (`alive_vehicle_ongoing`)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
