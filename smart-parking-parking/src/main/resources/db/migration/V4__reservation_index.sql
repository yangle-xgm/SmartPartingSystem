-- ============================================================
-- 报告 P1-16/P1-17：reservation 零索引 + community_id 无索引
-- 预约冲突判定按 (space_id, 时间段) 查询，absence of index 时全表扫描；
-- 多小区改造后 community_id 是高频过滤条件，同样需要索引。
-- ============================================================

ALTER TABLE reservation
    ADD KEY idx_space_time (space_id, start_time, end_time),
    ADD KEY idx_user_status (user_id, status);

ALTER TABLE parking_space ADD KEY idx_community (community_id);
