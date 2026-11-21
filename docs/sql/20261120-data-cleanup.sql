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
--
-- 【2026-11-21 修正】只把 status 改回 0 是不够的：种子的 entry_time 是 2026-09-20，
-- 而 OrderCompensationTask 每 10 分钟扫描「进行中且入场超过 24 小时」的订单并自动
-- 结算（这是它该做的事，不是 bug）。因此还原后 10 分钟内会被重新出场，数据又变回
-- 61 天 / 930 元。还原成「进行中」时必须同时把 entry_time 挪到今天，否则等于白改。
UPDATE parking_order
   SET exit_time = NULL, duration = NULL, amount = NULL, pay_status = 0, status = 0,
       entry_time = CASE id WHEN 100 THEN CONCAT(CURDATE(), ' 08:00:00')
                            WHEN 101 THEN CONCAT(CURDATE(), ' 09:15:00')
                            WHEN 102 THEN CONCAT(CURDATE(), ' 10:30:00')
                            ELSE CONCAT(CURDATE(), ' 11:45:00') END
 WHERE id IN (100, 101, 102, 103);

-- ---------- P2-2b 修正车辆品牌/颜色二次编码乱码 ----------
-- 与用户姓名同源：运行期经 E2E/客户端写入时按错误字符集落库，管理端车辆列表显示
-- å¤§ä¼— / ç™½è‰²。种子车辆（1-10）正常，只有运行期新建的 50-52 受影响。
UPDATE vehicle SET brand = '大众',   color = '白色' WHERE id = 50;
UPDATE vehicle SET brand = '丰田',   color = '黑色' WHERE id = 51;
UPDATE vehicle SET brand = '比亚迪', color = '白色' WHERE id = 52;

-- ---------- P3-4 清理预约表 E2E 残留 ----------
-- 种子里只有 100/101 两条（已由 E2E 取消），其余 9 条是 E2E 与手工测试产生。
DELETE FROM reservation WHERE id NOT IN (100, 101);

-- ---------- P3-1 校准当日统计行车位数 ----------
-- total_spaces 原只在有车进出时回填，E2E 建/删车位后停在 89，实际 87，占用率分母偏大。
UPDATE parking_stats
   SET total_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0),
       occupied_spaces = (SELECT COUNT(*) FROM parking_space WHERE is_deleted = 0 AND status = 1)
 WHERE stat_date = CURDATE();
