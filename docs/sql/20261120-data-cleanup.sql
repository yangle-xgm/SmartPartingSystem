-- 2026-11-20 数据清理（配合 backups/smart_parking-before-cleanup-20261120-1709.sql）
-- 幂等：重复执行不产生额外影响
SET NAMES utf8mb4;

-- ---------- P2-1 删除孤儿分润 ----------
-- share_income.payment_id 只有唯一索引、没有外键，早期硬删测试流水后留下孤儿，
-- 导致 owner 1004 的「我的共享收益」凭空多出 14.70 元。
DELETE s FROM share_income s
  LEFT JOIN payment_record p ON p.id = s.payment_id
 WHERE p.id IS NULL;

-- ---------- P2-2 修正二次编码乱码姓名 ----------
-- 运行期测试创建用户时客户端按 GBK 二次编码写入，库里是 mojibake，管理端显示 èµµå…­。
UPDATE sys_user SET real_name = '赵六'     WHERE id = 1004;
UPDATE sys_user SET real_name = '孙七'     WHERE id = 1005;
UPDATE sys_user SET real_name = '周八'     WHERE id = 1006;
UPDATE sys_user SET real_name = '滨江物业' WHERE id = 2002;
UPDATE sys_user SET real_name = '阳光物业' WHERE id = 2003;

-- ---------- P2-3 订单 100-103 还原到种子基线 ----------
-- 历史出场测试把它们改成了 59 天、金额 3594/4189.5/2992.5/3591，导致管理端
-- 「已关闭应收」虚高 14367 元。种子里是进行中、无出场、无金额。
UPDATE parking_order
   SET exit_time = NULL, duration = NULL, amount = NULL, pay_status = 0, status = 0
 WHERE id IN (100, 101, 102, 103);

-- ---------- P3-4 清理预约表 E2E 残留 ----------
-- 种子里只有 100/101 两条（已由 E2E 取消），其余 9 条是 E2E 与手工测试产生。
DELETE FROM reservation WHERE id NOT IN (100, 101);

-- ---------- P3-1 校准当日统计行车位数 ----------
-- total_spaces 原只在有车进出时回填，E2E 建/删车位后停在 89，实际 87，占用率分母偏大。
UPDATE parking_stats
   SET total_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0),
       occupied_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0 AND status = 1)
 WHERE stat_date = CURDATE();
