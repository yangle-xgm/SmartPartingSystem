# spec.md — 技术规范（设计阶段产出）

> 工件来源：AI 原生 SDLC「设计阶段」产出，承接 `intent.md`。所有不确定性在此公开化，避免开发阶段反复改方案。
> Source: 关联 `docs/sdlc/intent.md`

## Approach（核心方案）
采用 **工件驱动的闭环开发**：规划→设计→构建→测试→部署→维护六阶段首尾衔接。本项目为 Spring Cloud 多模块 Maven 聚合工程，
后端 12 微服务 + 前端（web/h5）。所有开发共识沉淀为版本可控文档（`AGENTS.md` / `intent.md` / `spec.md` / `plan.md` / `bands.yaml`），
并以 CI 构建/测试门禁 + 监控闭环替代“开会对齐”。

## 系统分层
```
前端(Vue3) → API网关(Spring Cloud Gateway: JWT/Sentinel/审计AOP)
   → 微服务(user/parking/vehicle/order/payment/notify/device/stats/ai)
      → MySQL(统一库) + Redis(缓存/会话/统计) + RabbitMQ(最终一致性) + Nacos(注册/配置) + MQTT(设备)
```

## Interfaces affected（受影响接口与契约）
- **对外接口**：各服务 `/api/**`（网关暴露）；网关白名单：`/api/user/login|register|captcha|refresh`。
- **内部接口**：`/internal/**`（OpenFeign 调用，网关不暴露），如 `vehicle → /internal/vehicle/info/{plateNo}`。
- **统一响应**：`Result<T>` / `PageResult<T>`（`common-core`）。错误码枚举 `ErrorCode`。
- **新增 AI 模块契约**：`/api/ai/plate/recognize`（上传图识别车牌，provider=mock|baidu）；`/api/ai/chat`（智能客服）。
- **新增业主自助服务契约（2026-11-17）**：`/api/vehicle/monthly/{quote,create,pay,orders}`（月卡报价/下单/支付/订单，支付幂等、重复支付被拒）；
  `/api/payment/share-income/my`（共享收益按 `owner_id` 汇总）；`/api/parking/space/my`（本人承租车位）；`/api/parking/reserve/my`、`/api/parking/reserve/cancel`（我的预约/取消）；
  `/api/parking/space/{id}/share`（业主自助开关共享）。上述接口的身份一律取自网关注入的 `X-User-Id`，不接受前端传参。

## 数据库设计（统一单库 smart_parking，17 张表）
计划书登记 11 张：`sys_user, parking_space, vehicle, billing_rule, parking_order, payment_record, device, notification, audit_log, parking_stats, visitor_invite`
**实现增量（需补文档）**：`community, reservation, share_income, feedback, payment_reconciliation, monthly_card_order`

### `monthly_card_order`（月卡订单，2026-11-17 新增，Flyway `V4__monthly_card_order.sql`）

与 `parking_order` 分表：停车订单是**按时计费的消费单**，月卡订单是**预付费的套餐单**，计费模型不同。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `order_no` | VARCHAR(32) | 月卡订单号 `MC+yyyyMMddHHmmss+6位随机`，唯一键 |
| `user_id` / `vehicle_id` | BIGINT UNSIGNED | 业主与车辆；**不建 vehicle 外键**——车辆解绑后订单仍需作为财务凭证保留 |
| `plate_no` | VARCHAR(128) | 车牌 AES 密文，与 `vehicle.plate_no` 同源 |
| `months` | TINYINT | 购买月数，枚举值 1/3/6/12 |
| `unit_price` | DECIMAL(10,2) | **下单时月单价快照**，后续 `billing_rule` 调价不影响历史订单 |
| `amount` | DECIMAL(10,2) | `unit_price × months` |
| `pay_status` | TINYINT | 0 待支付 / 1 成功 / 2 失败 / 3 已取消 |
| `pay_type` / `pay_no` / `transaction_id` | — | 支付方式（1 微信 2 支付宝）、模拟支付流水号、第三方交易号 |
| `expire_before` / `expire_after` | DATETIME | 支付前后月卡到期时间，用于对账与客诉追溯 |
| `pay_time` / `create_time` / `update_time` / `is_deleted` | — | 标准审计字段（逻辑删除） |

索引：`uk_monthly_order_no`、`idx_monthly_user`、`idx_monthly_vehicle`、`idx_monthly_pay_no`。
- 全表含 `is_deleted`（逻辑删除）；`表达是否`字段 `is_` 前缀；非负字段 `UNSIGNED`。
- 物理外键保留（删除策略 RESTRICT/SET NULL/CASCADE）。
- 索引已建：`idx_entry_time`、`idx_pay_status_time`、`idx_plate_no`、`idx_status_area` 等（见 `计划书.md` §4.3）。

## Open concerns（未解决问题 / 风险 — 必须人工确认）

> **2026-11-20 核查更新**：下列 1 / 2 / 4 / 5 已关闭（构建全绿 16 模块、ai 模块已登记、218 用例 + CI 门禁、支付宝已走真实沙箱）；3 部分关闭（`monthly_card_order` 已补字段表与 Flyway `V4`，`invoice` 仍缺 ER 图）；6 新增：月卡支付绕过 payment 服务，收入/发票漏账（见 `docs/边界确认-2026-11-20.md` §6.2）。原文保留以留时间线。

1. 🔴 构建在 `smart-parking-user` 中断（根因日志缺失），需本地 `mvn clean install` 复现定位。**（已关闭）**
2. 🟠 `smart-parking-ai` 模块未在架构文档登记（端口/职责/部署方式不明确）。
3. 🟠 增量表 `community/reservation/share_income/feedback/payment_reconciliation/monthly_card_order` 缺 ER 图与字段注释存档（`monthly_card_order` 已于 2026-11-18 补字段表，见上）。
4. 🟠 11/12 后端模块零单元测试；无 CI；无监控闭环。
5. 🟡 支付为“模拟”对接（微信/支付宝 simulate），真实退款/对账路径待联调。

## Explicitly rejected alternatives（已否决方案及原因）
- **Seata AT 分布式事务**：锁表、性能差 → 否决，采用本地事务 + RabbitMQ 最终一致性。
- **Elasticsearch 作为业务存储**：过度设计 → 否决，统计用 Redis Sorted Set + MQ 异步落 MySQL。
- **每个微服务独立数据库**：当前阶段运维成本高、联调成本大 → 否决，采用统一单库 + 物理外键。
- **用 AI 临时提示词开发**：无规范、易跑偏 → 否决，改为基于审批通过的 `plan.md` + `AGENTS.md` 规范开发。

## Sign-off（审核）
| 角色 | 结论 | 日期 |
| ---- | ---- | ---- |
| 架构/技术负责人 | 方案通过，须先补 `plan.md` 与 CI 门禁 | 2026-10-12 |
| （待补） | | |
