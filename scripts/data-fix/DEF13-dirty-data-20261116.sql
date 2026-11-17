-- ============================================================
-- DEF-13 脏数据清理（2026-11-16 执行版）
--
-- 依据：docs/数据治理-DEF13-脏数据清理.md
-- 执行时间：2026-11-16 12:30 (GMT+8)
-- 目标库：smart_parking
--
-- 排查结论（执行前实测）：
--   · 0 元支付流水 5 条，其中 pay_status=1（成功）1 条 → 软删除 1 条
--     其余 4 条 pay_status=2（已关闭的 0 元单支付尝试记录）不属于本口径，保留
--   · 重复通知 15 组（同一 user+type+content 在同一分钟内 2 条，2026-10-28 19:20:40 / 19:20:48）
--     → 每组保留最早 1 条，软删除 15 条
--   · 重复反馈 1 组（id 5/6，同内容 2026-10-28 19:20:40 / 19:20:48）
--     feedback 无 is_deleted，只能物理删除 → 备份后删 1 条（保留 id 5）
--
-- 回滚见文件末尾。
-- ============================================================

USE smart_parking;

-- ---------- 0. 备份（只备份将被改动的行） ----------
CREATE TABLE IF NOT EXISTS bak_20261116_payment_record  AS SELECT * FROM payment_record WHERE 1 = 0;
CREATE TABLE IF NOT EXISTS bak_20261116_notification    AS SELECT * FROM notification   WHERE 1 = 0;
CREATE TABLE IF NOT EXISTS bak_20261116_feedback        AS SELECT * FROM feedback       WHERE 1 = 0;

INSERT INTO bak_20261116_payment_record
SELECT * FROM payment_record WHERE amount = 0.00 AND pay_status = 1 AND is_deleted = 0;

INSERT INTO bak_20261116_notification
SELECT n.* FROM notification n
JOIN (
    SELECT user_id, type, MD5(content) AS h, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS bucket, MIN(id) AS keep_id
    FROM notification WHERE is_deleted = 0
    GROUP BY user_id, type, MD5(content), DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')
    HAVING COUNT(*) > 1
) d ON n.user_id = d.user_id AND n.type = d.type AND MD5(n.content) = d.h
   AND DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i') = d.bucket
WHERE n.id <> d.keep_id AND n.is_deleted = 0;

INSERT INTO bak_20261116_feedback
SELECT f.* FROM feedback f
JOIN (
    SELECT user_id, MD5(content) AS h, DATE(create_time) AS d, MIN(id) AS keep_id
    FROM feedback GROUP BY user_id, MD5(content), DATE(create_time) HAVING COUNT(*) > 1
) x ON f.user_id = x.user_id AND MD5(f.content) = x.h AND DATE(f.create_time) = x.d
WHERE f.id <> x.keep_id;

-- ---------- 1. 0 元成功支付流水：软删除 ----------
START TRANSACTION;
UPDATE payment_record
SET is_deleted = 1
WHERE amount = 0.00 AND pay_status = 1 AND is_deleted = 0;
COMMIT;

-- ---------- 2. 重复通知：保留每组最早一条，其余软删除 ----------
START TRANSACTION;
UPDATE notification n
JOIN (
    SELECT user_id, type, MD5(content) AS h, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS bucket, MIN(id) AS keep_id
    FROM notification WHERE is_deleted = 0
    GROUP BY user_id, type, MD5(content), DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')
    HAVING COUNT(*) > 1
) d ON n.user_id = d.user_id AND n.type = d.type AND MD5(n.content) = d.h
   AND DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i') = d.bucket
SET n.is_deleted = 1
WHERE n.id <> d.keep_id AND n.is_deleted = 0;
COMMIT;

-- ---------- 3. 重复反馈：保留最早一条，其余物理删除（已备份） ----------
START TRANSACTION;
DELETE f FROM feedback f
JOIN (
    SELECT user_id, MD5(content) AS h, DATE(create_time) AS d, MIN(id) AS keep_id
    FROM feedback GROUP BY user_id, MD5(content), DATE(create_time) HAVING COUNT(*) > 1
) x ON f.user_id = x.user_id AND MD5(f.content) = x.h AND DATE(f.create_time) = x.d
WHERE f.id <> x.keep_id;
COMMIT;

-- ---------- 4. 校验 ----------
-- 4.1 0 元成功流水应为 0
SELECT COUNT(*) AS zero_amount_success_left
FROM payment_record WHERE amount = 0.00 AND pay_status = 1 AND is_deleted = 0;
-- 4.2 重复通知应为 0 组
SELECT COUNT(*) AS dup_notify_groups_left FROM (
    SELECT 1 FROM notification WHERE is_deleted = 0
    GROUP BY user_id, type, MD5(content), DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')
    HAVING COUNT(*) > 1) t;
-- 4.3 重复反馈应为 0 组
SELECT COUNT(*) AS dup_feedback_groups_left FROM (
    SELECT 1 FROM feedback GROUP BY user_id, MD5(content), DATE(create_time) HAVING COUNT(*) > 1) t;
-- 4.4 备份行数应与清理行数一致（1 / 15 / 1）
SELECT (SELECT COUNT(*) FROM bak_20261116_payment_record) AS bak_pay,
       (SELECT COUNT(*) FROM bak_20261116_notification)   AS bak_notify,
       (SELECT COUNT(*) FROM bak_20261116_feedback)       AS bak_feedback;

-- ============================================================
-- 回滚（如需）
-- ============================================================
-- UPDATE payment_record p JOIN bak_20261116_payment_record b ON b.id = p.id
--   SET p.is_deleted = b.is_deleted;
-- UPDATE notification n JOIN bak_20261116_notification b ON b.id = n.id
--   SET n.is_deleted = b.is_deleted;
-- INSERT INTO feedback SELECT * FROM bak_20261116_feedback f
--   WHERE NOT EXISTS (SELECT 1 FROM feedback x WHERE x.id = f.id);
--
-- 回滚确认无误后可：DROP TABLE bak_20261116_payment_record, bak_20261116_notification, bak_20261116_feedback;
