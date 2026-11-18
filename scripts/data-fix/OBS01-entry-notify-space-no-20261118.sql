-- ============================================================================
-- OBS-01 历史通知文案修复：「停放在」后缺车位号
--
-- 背景：bbf2ae0 之前，入场通知的报文没有带 spaceNo，模板直接拼成
--       「您的访客车辆 京B80950 已于 2026-11-17 10:02:30 入场，停放在」，
--       句末车位号为空。修复后新产生的通知已正常显示「停放在 A-005」。
--       本脚本只处理**存量脏数据**，不涉及任何代码逻辑。
--
-- 口径：车位号已经丢失、无法反推，因此不做「回填」，只把半句截断成完整句：
--       「……入场，停放在」 → 「……入场。」
--
-- 幂等：条件限定「以『，停放在』结尾」，执行一次后不再命中，可重复执行。
--       兼容半角逗号与末尾空格两种历史写法。
--
-- 执行：mysql -uroot -p smart_parking < scripts/data-fix/OBS01-entry-notify-space-no-20261118.sql
-- ============================================================================

-- ① 执行前快照（仅查看，不修改）
SELECT '执行前' AS stage,
       COUNT(*) AS dirty_rows
  FROM notification
 WHERE is_deleted = 0
   AND (content LIKE '%，停放在' OR content LIKE '%,停放在' OR content LIKE '%停放在 ');

-- ② 备份受影响行到临时备份表（便于回滚；重复执行时先删同名表）
DROP TABLE IF EXISTS bak_20261118_notification_space_no;
CREATE TABLE bak_20261118_notification_space_no AS
SELECT * FROM notification
 WHERE is_deleted = 0
   AND (content LIKE '%，停放在' OR content LIKE '%,停放在' OR content LIKE '%停放在 ');

-- ③ 截断半句：按「去尾空格 → 去『停放在』→ 再去尾空格 → 去尾逗号」的顺序剥离，
--    最后补句号。注意顺序不能反：先剥逗号会因为句末是「在」而匹配不到。
UPDATE notification
   SET content = CONCAT(
         TRIM(TRAILING '，' FROM
           TRIM(TRAILING ',' FROM
             TRIM(TRAILING ' ' FROM
               TRIM(TRAILING '停放在' FROM
                 TRIM(TRAILING ' ' FROM content))))),
         '。')
 WHERE is_deleted = 0
   AND (content LIKE '%，停放在' OR content LIKE '%,停放在' OR content LIKE '%停放在 ')
   AND content NOT LIKE '%。';

-- ④ 执行后校验：应为 0
SELECT '执行后' AS stage,
       COUNT(*) AS dirty_rows
  FROM notification
 WHERE is_deleted = 0
   AND (content LIKE '%，停放在' OR content LIKE '%,停放在' OR content LIKE '%停放在 ');

-- ⑤ 抽查 3 条结果（MySQL 不支持带 LIMIT 的 IN 子查询，改用 JOIN 派生表）
SELECT n.id, LEFT(n.content, 90) AS content_after
  FROM notification n
  JOIN (SELECT id FROM bak_20261118_notification_space_no LIMIT 3) b ON b.id = n.id;

-- 回滚（如需）：从备份表恢复
-- UPDATE notification n JOIN bak_20261118_notification_space_no b ON b.id = n.id
--    SET n.content = b.content;
