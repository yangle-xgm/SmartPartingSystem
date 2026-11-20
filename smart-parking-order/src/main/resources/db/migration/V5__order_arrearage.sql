-- 出场分级放行后的欠费追踪
-- remind_time: 出场后 24h 未支付发一次催缴，避免定时任务每轮重复发送
-- arrear_status: 出场后 48h 仍未支付即记欠费，作为黑名单判定依据
ALTER TABLE parking_order
    ADD COLUMN remind_time DATETIME NULL COMMENT '待支付提醒发送时间（出场后24h催缴，NULL=未发）' AFTER pay_time,
    ADD COLUMN arrear_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '欠费标记（0正常 1已记欠费）' AFTER remind_time;

CREATE INDEX idx_order_arrear ON parking_order (arrear_status, pay_status, vehicle_id);
