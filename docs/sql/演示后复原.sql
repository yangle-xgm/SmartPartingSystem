-- ============================================================
-- 答辩演示 · 演示后复原（幂等，可反复执行，演练多次必用）
--
-- 作用：把演示期间产生的订单、流水、分润、通知、车位占用全部清掉，
--       种子数据（订单 100-110 / 流水 101-105 / 分润 1-2）保持原样。
--
-- 判据：create_time >= CURDATE()。历史数据全部创建于 2026-11-18 及更早，
--       所以"今天创建"等价于"演示新增"，不会误删种子。
--
-- 执行：
--   cd "D:/JAVA EE/SmartPartingSystem"
--   docker exec -i sp-mysql mysql -uroot -p21491879 --default-character-set=utf8mb4 \
--     smart_parking < docs/sql/演示后复原.sql
--
-- 执行后看板应回到：今日入场 4 / 今日出场 0 / 今日收入 0 / 当前在场 21
-- ============================================================
SET NAMES utf8mb4;
START TRANSACTION;

-- ---------- 1. 收集本次演示新增的订单 ----------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_order;
CREATE TEMPORARY TABLE tmp_demo_order AS
SELECT id, space_id
  FROM parking_order
 WHERE is_deleted = 0
   AND create_time >= CURDATE()
   AND id NOT BETWEEN 100 AND 110;

-- ---------- 2. 删分润（只删挂在演示流水上的，种子分润 1-2 不动） ----------
DELETE s FROM share_income s
  JOIN payment_record p ON p.id = s.payment_id
 WHERE p.create_time >= CURDATE();

-- ---------- 3. 删支付流水 ----------
DELETE FROM payment_record WHERE create_time >= CURDATE();

-- ---------- 4. 释放演示占用的车位 ----------
-- 入场时的车位来自"空闲车位"分配，不与任何进行中订单共享，
-- 所以直接按演示订单的 space_id 置回空闲即可。
UPDATE parking_space ps
  JOIN tmp_demo_order d ON d.space_id = ps.id
   SET ps.status = 0, ps.update_time = NOW()
 WHERE ps.is_deleted = 0;

-- ---------- 5. 删演示产生的通知 ----------
DELETE FROM notification WHERE create_time >= CURDATE();

-- ---------- 6. 删演示订单 ----------
DELETE FROM parking_order WHERE id IN (SELECT id FROM tmp_demo_order);

-- ---------- 7. 订单 100-103 还原为进行中 ----------
-- 演示第 ③ 步会把 100 号结算掉，这里退回未出场、无金额、未支付。
-- entry_time 保留（今天 08:00/09:15/10:30/11:45），离现在不足 24 小时，
-- 不会被 OrderCompensationTask 判定超时再结算一次。
UPDATE parking_order
   SET exit_time = NULL, duration = NULL, amount = NULL,
       pay_status = 0, status = 0, update_time = NOW()
 WHERE id BETWEEN 100 AND 103;

DROP TEMPORARY TABLE IF EXISTS tmp_demo_order;

-- ---------- 8. 今日统计按事实重算 ----------
UPDATE parking_stats s
   SET s.total_spaces    = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0),
       s.occupied_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0 AND status = 1),
       s.entry_count     = (SELECT COUNT(*) FROM parking_order WHERE is_deleted = 0 AND DATE(entry_time) = CURDATE()),
       s.exit_count      = (SELECT COUNT(*) FROM parking_order WHERE is_deleted = 0 AND DATE(exit_time) = CURDATE()),
       s.total_income    = (SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE pay_status = 1 AND DATE(pay_time) = CURDATE()),
       s.update_time     = NOW()
 WHERE s.stat_date = CURDATE();

COMMIT;

-- ---------- 9. 校验 ----------
SELECT stat_date, total_spaces, occupied_spaces, entry_count, exit_count, total_income
  FROM parking_stats WHERE stat_date = CURDATE();
SELECT id, entry_time, exit_time, amount, pay_status, status
  FROM parking_order WHERE id BETWEEN 100 AND 103 ORDER BY id;
SELECT '订单总数' AS item, COUNT(*) AS cnt FROM parking_order WHERE is_deleted = 0
UNION ALL SELECT '支付流水', COUNT(*) FROM payment_record
UNION ALL SELECT '分润', COUNT(*) FROM share_income
UNION ALL SELECT '今日通知', COUNT(*) FROM notification WHERE create_time >= CURDATE();
