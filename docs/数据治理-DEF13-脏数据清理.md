# DEF-13 脏数据清理方案（8-10 月 E2E 遗留）

> 来源：`docs/前端功能测试报告-2026-10-29.md` DEF-13。
> **状态：已于 2026-11-16 12:31 执行完毕**（脚本 `scripts/data-fix/DEF13-dirty-data-20261116.sql`，含备份表与回滚段）。
> 执行结果：0 元成功支付流水软删 1 条、重复通知软删 15 条、重复反馈物理删 1 条（保留最早一条）；4 条 `pay_status=2` 的 0 元*未成功*流水按口径保留。清理后三类校验计数均为 0，备份表 `bak_20261116_*` 行数 1/15/1 与预期一致。
> 以下 §2/§3/§4 保留为口径说明与回滚依据。

## 1. 问题来源

2026-08 至 2026-10 的端到端测试反复走「入场 → 出场 → 支付 → 通知」链路，累计留下三类脏数据：

| 类别 | 表现 | 成因 |
| --- | --- | --- |
| 0 元支付流水 | `payment_record.amount = 0.00` 且 `pay_status = 1`，页面上出现「¥0.00」的成功支付 | 修复前 0 元订单（免费时长内出场）仍生成成功流水；2026-10-28 已改为 0 元单不生成流水，但历史记录未回滚 |
| 重复通知 | 同一 `user_id` + `type` + `content` 在数秒内出现多条 | 入场/出场链路重试时重复投递，消费者未按业务键去重 |
| 重复反馈 | 同一 `user_id` + `content` 同日多条 | 测试脚本连点提交 |

这些数据不影响功能正确性，但会污染统计口径（收入合计、通知未读数）与演示效果。

## 2. 排查（只读，可随时执行）

```sql
-- 2.1 0 元支付流水（应为 0 条；如有即为脏数据）
SELECT id, pay_no, order_id, user_id, amount, pay_status, pay_time, create_time
FROM payment_record
WHERE amount = 0.00 AND is_deleted = 0
ORDER BY create_time;

-- 2.2 0 元流水对应的订单，确认订单本身是否 0 元单
SELECT o.id, o.order_no, o.total_amount, o.pay_status, p.id AS pay_id, p.amount
FROM payment_record p
JOIN parking_order o ON o.id = p.order_id
WHERE p.amount = 0.00 AND p.is_deleted = 0;

-- 2.3 重复通知（同一用户同类型同内容 60 秒内多条，保留最早一条）
SELECT user_id, type, MD5(content) AS content_hash, COUNT(*) AS cnt,
       MIN(id) AS keep_id, MIN(create_time) AS first_at, MAX(create_time) AS last_at
FROM notification
WHERE is_deleted = 0
GROUP BY user_id, type, MD5(content), DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')
HAVING COUNT(*) > 1
ORDER BY cnt DESC;

-- 2.4 重复反馈（同一用户同内容同日多条，保留最早一条）
SELECT user_id, MD5(content) AS content_hash, COUNT(*) AS cnt,
       MIN(id) AS keep_id, MIN(create_time) AS first_at
FROM feedback
GROUP BY user_id, MD5(content), DATE(create_time)
HAVING COUNT(*) > 1
ORDER BY cnt DESC;
```

## 3. 清理（需授权，按顺序执行）

> 前置：先做备份（第 3.0 步），再开事务；每一步都先跑 `SELECT` 确认影响行数。

```sql
-- 3.0 备份（推荐；至少备份将被改动的行）
CREATE TABLE bak_20261029_payment_record  AS SELECT * FROM payment_record  WHERE 1 = 0;
INSERT INTO bak_20261029_payment_record   SELECT * FROM payment_record   WHERE amount = 0.00 AND is_deleted = 0;
CREATE TABLE bak_20261029_notification    AS SELECT * FROM notification    WHERE 1 = 0;
CREATE TABLE bak_20261029_feedback        AS SELECT * FROM feedback        WHERE 1 = 0;

-- 3.1 0 元流水：软删除（payment_record 有 is_deleted，不做物理删除）
START TRANSACTION;
UPDATE payment_record
SET is_deleted = 1
WHERE amount = 0.00 AND pay_status = 1 AND is_deleted = 0;
-- 核对：SELECT ROW_COUNT(); 与第 2.1 步行数一致才 COMMIT
COMMIT;

-- 3.2 重复通知：保留每组最早一条，其余软删除
START TRANSACTION;
INSERT INTO bak_20261029_notification
SELECT n.* FROM notification n
JOIN (
  SELECT user_id, type, MD5(content) AS h, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS bucket, MIN(id) AS keep_id
  FROM notification WHERE is_deleted = 0
  GROUP BY user_id, type, h, bucket HAVING COUNT(*) > 1
) d
  ON n.user_id = d.user_id AND n.type = d.type AND MD5(n.content) = d.h
 AND DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i') = d.bucket
WHERE n.id <> d.keep_id AND n.is_deleted = 0;

UPDATE notification n
JOIN (
  SELECT user_id, type, MD5(content) AS h, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS bucket, MIN(id) AS keep_id
  FROM notification WHERE is_deleted = 0
  GROUP BY user_id, type, h, bucket HAVING COUNT(*) > 1
) d
  ON n.user_id = d.user_id AND n.type = d.type AND MD5(n.content) = d.h
 AND DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i') = d.bucket
SET n.is_deleted = 1
WHERE n.id <> d.keep_id AND n.is_deleted = 0;
COMMIT;

-- 3.3 重复反馈：feedback 无 is_deleted，只能物理删除，务必先备份
START TRANSACTION;
INSERT INTO bak_20261029_feedback
SELECT f.* FROM feedback f
JOIN (
  SELECT user_id, MD5(content) AS h, DATE(create_time) AS d, MIN(id) AS keep_id
  FROM feedback GROUP BY user_id, h, d HAVING COUNT(*) > 1
) x
  ON f.user_id = x.user_id AND MD5(f.content) = x.h AND DATE(f.create_time) = x.d
WHERE f.id <> x.keep_id;

DELETE f FROM feedback f
JOIN (
  SELECT user_id, MD5(content) AS h, DATE(create_time) AS d, MIN(id) AS keep_id
  FROM feedback GROUP BY user_id, h, d HAVING COUNT(*) > 1
) x
  ON f.user_id = x.user_id AND MD5(f.content) = x.h AND DATE(f.create_time) = x.d
WHERE f.id <> x.keep_id;
COMMIT;
```

## 4. 回滚

```sql
-- 0 元流水
UPDATE payment_record p
JOIN bak_20261029_payment_record b ON b.id = p.id
SET p.is_deleted = b.is_deleted;

-- 重复通知
UPDATE notification n
JOIN bak_20261029_notification b ON b.id = n.id
SET n.is_deleted = b.is_deleted;

-- 重复反馈（物理删除的部分需重新插入）
INSERT INTO feedback SELECT * FROM bak_20261029_feedback f
WHERE NOT EXISTS (SELECT 1 FROM feedback x WHERE x.id = f.id);
```

回滚完成后可 `DROP TABLE bak_20261029_*`。

## 5. 为什么代码侧不自动清理

- **不可逆**：`feedback` 无逻辑删除字段，物理删除后只能靠备份恢复；
- **口径需业务确认**：「同一内容同日多条」在真实场景也可能是用户真的提交了两次；
- **当前环境无 DB 凭据授权**：本次修复批次只做代码与文档，不对运行库执行写操作。

## 6. 防复发（已在代码侧落地）

| 措施 | 位置 | 状态 |
| --- | --- | --- |
| 0 元订单不生成支付流水 | `smart-parking-payment`（2026-10-28 修复） | ✅ 已上线 |
| 反馈回复不存在/已删返回 5702，空回复返回 4001 | `FeedbackController.reply` | ✅ 本次修复 |
| 设备状态更新目标不存在返回 5601，不再静默成功 | `DeviceServiceImpl.updateStatus` | ✅ 本次修复 |
| 关单/支付回调幂等 | `ParkingOrderServiceImpl` / 支付回调 | 已有 |
| 通知按业务键去重 | 未做（消费侧幂等可后续补） | 🟠 建议后续 |
