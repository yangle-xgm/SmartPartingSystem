-- ============================================================
-- 报告 P0-9：分润幂等 —— share_income.payment_id 唯一约束
--
-- 背景：paymentSuccess 先读 pay_status==1 再写，非原子；并发/渠道重试时两个事务
-- 都能通过检查，各插一条分润 → 同一笔支付给业主重复分钱。
-- 应用层已改为条件更新（WHERE pay_status=0，affected=0 即返回），
-- 数据库层再加唯一键兜底，双保险。
-- ============================================================

-- 1. 清理历史重复分润：同一 payment_id 保留最早一条
DELETE s1 FROM share_income s1
JOIN share_income s2 ON s1.payment_id = s2.payment_id AND s1.id > s2.id;

-- 2. 唯一约束
ALTER TABLE share_income ADD UNIQUE KEY uk_payment_id (payment_id);

-- 3. 对账/统计按时间扫描的索引（报告 P2-22：日终对账全表扫描）
ALTER TABLE payment_record ADD KEY idx_pay_time (pay_time);
