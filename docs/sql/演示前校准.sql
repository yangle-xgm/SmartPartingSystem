-- ============================================================
-- 答辩演示 · 演示前校准（幂等，可反复执行）
--
-- 作用：只做两件事 —— 清一条历史脏订单、把「今日统计」改成与订单事实一致。
--       不碰任何种子业务数据（订单 100-103、流水、车位、车辆都不动）。
--
-- 执行：
--   cd "D:/JAVA EE/SmartPartingSystem"
--   docker exec -i sp-mysql mysql -uroot -p21491879 --default-character-set=utf8mb4 \
--     smart_parking < docs/sql/演示前校准.sql
--
-- 执行后看板应为：今日入场 4 / 今日出场 0 / 今日收入 0 / 当前在场 21
-- ============================================================
SET NAMES utf8mb4;
START TRANSACTION;

-- ---------- 1. 清一条历史脏订单 ----------
-- 110 号：2026-09-19 入场后从未出场，pay_status 却是 2（已退款），且没有任何支付流水。
-- 这是早期出场测试的残留，业主端会把它显示成一笔"进行中"，点进去状态自相矛盾。
-- 软删（is_deleted=1），保留痕迹可追溯。
UPDATE parking_order
   SET is_deleted = 1, update_time = NOW()
 WHERE id = 110 AND is_deleted = 0;

-- ---------- 2. 今日统计按事实重算 ----------
-- 100-103 四单的 entry_time 被挪到今天（避免被补偿任务判定超时结算），
-- 但 parking_stats 不会因此自动 +4，看板就会显示「今日入场 2、进行中 4」。
-- 这里按订单与流水的事实重算，口径与业务一致，不是编数字。
UPDATE parking_stats s
   SET s.total_spaces    = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0),
       s.occupied_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0 AND status = 1),
       s.entry_count     = (SELECT COUNT(*) FROM parking_order WHERE is_deleted = 0 AND DATE(entry_time) = CURDATE()),
       s.exit_count      = (SELECT COUNT(*) FROM parking_order WHERE is_deleted = 0 AND DATE(exit_time) = CURDATE()),
       s.total_income    = (SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE pay_status = 1 AND DATE(pay_time) = CURDATE()),
       s.update_time     = NOW()
 WHERE s.stat_date = CURDATE();

COMMIT;

-- ---------- 3. 校验 ----------
SELECT stat_date, total_spaces, occupied_spaces, entry_count, exit_count, total_income
  FROM parking_stats WHERE stat_date = CURDATE();
SELECT '进行中订单' AS item, COUNT(*) AS cnt FROM parking_order WHERE is_deleted = 0 AND status = 0;
SELECT '待支付流水' AS item, COUNT(*) AS cnt FROM payment_record WHERE pay_status = 0;
