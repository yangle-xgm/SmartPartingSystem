# AGENTS.md — 智慧停车系统（SmartPartingSystem）统一 AI 开发规范

> 本文件是 **跨工具通用配置**（Claude Code / OpenAI Codex / Gemini CLI / WorkBuddy 均读取）。
> 任何 AI 智能体在本仓库工作前，必须通读本文件及 `docs/sdlc/` 下的 `intent.md`、`spec.md`、`plan.md`、`bands.yaml`，
> 并严格遵守「工件驱动、闭环迭代」的 AI 原生 SDLC 原则：所有共识沉淀为版本可控文档，不靠口头沟通。

---

## 1. 项目身份（Project Identity）

- **名称**：小区智慧停车管理系统（SmartPartingSystem）
- **形态**：Spring Cloud 微服务单体仓库（多模块 Maven 聚合工程），含后端 12 个模块 + 前端 `smart-parking-web` + H5 `smart-parking-h5`
- **JDK**：17 ｜ **构建**：Maven 3.9+ ｜ **目标**：`mvn clean install` 全量通过 + `mvn test` 全量通过

## 2. 技术栈（ authoritative，改版本需先改 plan.md）

| 层 | 技术 | 版本 | 备注 |
| -- | ---- | ---- | ---- |
| 后端框架 | Spring Boot | 3.2.4 | |
| 微服务 | Spring Cloud | 2023.0.1 | |
| 微服务增强 | Spring Cloud Alibaba | 2023.0.1.0 | Nacos / Sentinel |
| ORM | MyBatis-Plus | 3.5.5 | |
| 数据库 | MySQL | 8.0 | 统一单库 `smart_parking` |
| 缓存 | Redis | 7.x | |
| 消息 | RabbitMQ | 3.13 | 最终一致性 |
| 注册/配置 | Nacos | 2.3.1 | |
| 前端 | Vue3 + Element Plus + Vite | 3.4 / 2.7 / 5.x | |

> ⚠️ **硬性约束**：Spring Boot / Cloud / Cloud Alibaba 三者版本必须严格对应（见 `计划书.md` §2.3）。**禁止单独升级其中一个**。

## 3. 模块边界（谁拥有什么 — 改表前先看这里）

| 模块 | 端口 |  owns 表 | 职责 |
| ---- | ---- | -------- | ---- |
| smart-parking-gateway | 8080 | — | 路由 / JWT 鉴权 / Sentinel 限流 / 操作日志 AOP |
| smart-parking-user | 8081 | sys_user, audit_log, community, feedback | 用户/权限/审计/小区/反馈 |
| smart-parking-parking | 8082 | parking_space, reservation | 车位/预约 |
| smart-parking-vehicle | 8083 | vehicle | 车辆/车牌识别 |
| smart-parking-order | 8084 | parking_order, billing_rule, visitor_invite | 订单/计费/访客邀请 |
| smart-parking-payment | 8085 | payment_record, payment_reconciliation, share_income | 支付/对账/分润 |
| smart-parking-notify | 8086 | notification | 通知 |
| smart-parking-device | 8087 | device | 设备/MQTT/道闸 |
| smart-parking-stats | 8088 | parking_stats | 统计（Redis Sorted Set 实时 + MQ 落库） |
| smart-parking-ai | — | — | 车牌识别增强 / 智能客服（**未在计划书登记，属范围增量**） |
| smart-parking-common-* | — | — | core(Result/PageResult/AES/脱敏/异常) / redis / swagger |

> **跨服务数据访问铁律**：服务只能通过自己的 Mapper 访问自己 owns 的表；跨服务取数必须用 OpenFeign 调 `/internal/**` 接口，网关不对外暴露 `/internal/**`。

## 4. 不可破坏的架构契约（Do-Not-Break）

1. **双 Token**：`accessToken`(2h) + `refreshToken`(7d)；前端 401 静默续期。
2. **敏感字段 AES-256 存储**：手机号/车牌号/身份证号用 MyBatis-Plus `TypeHandler` 加解密；API 层 `@Sensitive` 注解脱敏（管理员可见完整）。密码用 BCrypt 单向哈希。
3. **分布式事务 = 本地事务 + RabbitMQ 最终一致性**，**禁止引入 Seata AT**（锁表）。支付超时由定时任务每 5 分钟补偿查询。
4. **统一响应**：所有接口返回 `Result<T>` / `PageResult<T>`（见 `common-core`）。
5. **逻辑删除**：所有业务表含 `is_deleted`；物理外键保留参照完整性。
6. **网关白名单**：登录/注册/验证码/refresh 免鉴权；其余接口必须 JWT。
7. **监控闭环**：核心接口必须配置 `bands.yaml` 指标区间，越界自动生成 `intent.md`（见维护阶段）。

## 5. 构建与测试（Build & Test — AI 编辑后必须触发）

```bash
# 全量构建（跳过测试用于快速验证编译）
mvn -B clean install -DskipTests

# 全量测试（无测试模块的模块用 failIfNoTests=false 避免误红）
mvn -B test -DfailIfNoTests=false

# 仅构建/测试单个受影响模块
mvn -B -pl smart-parking-order -am test -DfailIfNoTests=false
```

- 测试默认走 `scripts/run-tests.sh`（被 `.claude/settings.json` 钩子与 CI 共用）。
- 新增业务逻辑**必须**配套单元测试（参考 `smart-parking-order/.../BillingAndErrorCodeTest.java` 的纯逻辑测试范式）。
- 编译必须开 `-parameters`（Spring 6 参数名反射依赖），父 POM 已配置。

## 6. 编码标准

- Java 17；优先 `record`/不可变对象；Lombok（`@RequiredArgsConstructor` 等）仅用于 PO/DTO/Query。
- DTO/VO/PO 分层：**PO**(表映射) / **DTO**(入参) / **Query**(分页查询) / **VO**(出参)。
- 异常统一抛 `BizException` + `ErrorCode`（枚举），由 `GlobalExceptionHandler` 转 `Result`。
- MapStruct 做 DTO↔PO 转换；禁止手写大量 setter 拷贝。
- 日志用 SLF4J；敏感信息（手机号/车牌）**禁止明文打印**。

## 7. 变更流程（每条 PR 必须）

1. 先看 `docs/sdlc/intent.md` → `spec.md` → `plan.md` 是否覆盖本次需求；未覆盖先补工件（人工确认）。
2. 基于 `plan.md` 的「Files to change」精准改动，**禁止临时随意开发**。
3. 编辑后自动触发 `scripts/run-tests.sh`（钩子/CI）。
4. PR 描述附：`spec.md` 关联、测试结论、回滚方案。
5. 高危变更（认证/支付/库迁移/基础设施）**必须人工终审**，低风险配置/文档可自动合并。

## 8. AI 审查五维检查清单（每次 PR 自评）

- [ ] **正确性**：接口契约、空值、越界、并发（如车位状态并发更新）、事务边界
- [ ] **效率**：N+1 查询、Redis 缓存命中、MQ 异步解耦是否到位
- [ ] **可维护性**：分层清晰、命名一致、无魔法值、异常可追踪
- [ ] **安全性**：敏感字段加解密/脱敏、JWT 校验、SQL 注入（用 MyBatis-Plus 占位符）、越权（角色码）
- [ ] **综合能力**：是否符合本 `AGENTS.md` 契约、是否破坏跨服务边界、是否需同步文档

## 9. 当前已知债务（实现前必读 `docs/sdlc/plan.md`）

- 🔴 **构建中断**：上次构建在 `smart-parking-user` 模块失败（见 `dependency-analyze.txt`），根因日志被截断，需本地 `mvn clean install` 复现定位。
- 🟠 **文档与实现漂移**：DB 实际含 `community/reservation/share_income/feedback/payment_reconciliation` 等计划书之外的表；`smart-parking-ai` 模块未登记进任何架构文档。
- 🟠 **测试极缺**：仅 `smart-parking-order` 有 1 个测试类，其余 11 模块零测试。
- 🟠 **无 CI / 无监控闭环**：无 `.github` 工作流，无 `bands.yaml` 落库与告警→需求回流。
