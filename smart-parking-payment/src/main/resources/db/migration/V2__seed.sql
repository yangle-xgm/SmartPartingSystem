-- ============================================================
-- 模块 smart-parking-payment 种子数据（幂等 INSERT IGNORE）
-- ============================================================

INSERT IGNORE INTO `payment_record` (`id`, `pay_no`, `order_id`, `user_id`, `amount`, `pay_type`, `pay_status`, `transaction_id`, `pay_time`) VALUES
(101, 'PAY202609160001', 104, 1001, 5.00,  1, 1, 'WX202609160001', '2026-09-16 10:05:00'),
(102, 'PAY202609160002', 105, 1003, 50.00, 2, 1, 'ZFB202609160001', '2026-09-16 20:50:00'),
(103, 'PAY202609170001', 106, 1001, 10.00, 1, 1, 'WX202609170001', '2026-09-17 11:35:00'),
(104, 'PAY202609170002', 107, 1001, 15.00, 2, 1, 'ZFB202609170001', '2026-09-17 16:35:00'),
(105, 'PAY202609180001', 109, 1002, 2.00,  1, 3, 'WX202609180002', '2026-09-18 16:10:00');

INSERT IGNORE INTO `payment_reconciliation` (`recon_date`, `pay_count`, `total_amount`, `refund_count`, `refund_amount`, `diff_amount`, `status`, `remark`) VALUES
('2026-09-18', 3, 65.00, 0, 0.00, 0.00, 0, '对账正常'),
('2026-09-19', 2, 17.00, 1, 2.00, 0.00, 0, '含一笔退款'),
('2026-09-20', 1, 2.00, 0, 0.00, 0.00, 0, '对账正常');

INSERT IGNORE INTO `share_income` (`id`, `space_id`, `owner_id`, `payment_id`, `total_amount`, `owner_ratio`, `owner_amount`, `property_amount`) VALUES
(1, 1, 1001, 101, 5.00, 0.70, 3.50, 1.50),
(2, 2, 1002, 103, 10.00, 0.70, 7.00, 3.00);
