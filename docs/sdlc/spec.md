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

## 数据库设计（统一单库 smart_parking，16 张表）
计划书登记 11 张：`sys_user, parking_space, vehicle, billing_rule, parking_order, payment_record, device, notification, audit_log, parking_stats, visitor_invite`
**实现增量（需补文档）**：`community, reservation, share_income, feedback, payment_reconciliation`
- 全表含 `is_deleted`（逻辑删除）；`表达是否`字段 `is_` 前缀；非负字段 `UNSIGNED`。
- 物理外键保留（删除策略 RESTRICT/SET NULL/CASCADE）。
- 索引已建：`idx_entry_time`、`idx_pay_status_time`、`idx_plate_no`、`idx_status_area` 等（见 `计划书.md` §4.3）。

## Open concerns（未解决问题 / 风险 — 必须人工确认）
1. 🔴 构建在 `smart-parking-user` 中断（根因日志缺失），需本地 `mvn clean install` 复现定位。
2. 🟠 `smart-parking-ai` 模块未在架构文档登记（端口/职责/部署方式不明确）。
3. 🟠 增量表 `community/reservation/share_income/feedback/payment_reconciliation` 缺 ER 图与字段注释存档。
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
