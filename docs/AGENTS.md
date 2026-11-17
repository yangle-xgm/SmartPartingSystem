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
| smart-parking-ai | 8090 | — | 车牌识别增强（百度 OCR 真实接入，provider=baidu 需配 AK/SK，缺省降级 mock）/ 智能客服（豆包火山方舟真实接入，provider=doubao 需配 API Key + 推理接入点 ep-xxxx ID，缺省/异常降级 mock） |
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

> 最近评估：2026-10-13。构建已全绿、CI 已激活、`bands.yaml` 骨架已落地；以下为**仍待解决**的真实缺口（勿与已关闭项混淆）。

- 🔴 **集成/API 测试近乎为零**：仅有 ~10 个纯逻辑单测；`user / notify / device / stats / ai` 模块零测试，全仓此前无 `@WebMvcTest`/`@SpringBootTest` 集成测试。CI 用 `mvn test -DfailIfNoTests=false`，无测试模块会"假绿"，且无 JaCoCo 覆盖率硬门槛（已加软门槛，待硬化）。
- 🟢 **Phase D 监控闭环已接线且业务埋点已完成**：`stats` + `gateway/order/payment/parking/device` 五个服务均接入 `actuator`+`micrometer-registry-prometheus` 暴露 `/actuator/prometheus`（带 `application` 标签）；`common-core` 的 `PrometheusMetricSource` 调 Prometheus `/api/v1/query`（无数据返回 `NaN` 不误报）；`MonitorAutoConfiguration` 周期驱动 `MonitorLoop` 评估 `bands.yaml`，越界写 `intent-*.md`；`docker-compose` 已加 `prometheus`+`grafana`。业务埋点：`payment_recon_diff_amount`(PaymentReconMetrics)、`space_cache_miss_rate_pct`(ParkingCacheMetrics)、`device_offline_rate_pct`(DeviceOnlineMetrics)、`mq_order_backlog`/`mysql_active_connections`(OrderBusinessMetrics) 四个手写 Gauge，HTTP 延迟/错误率由 Actuator 自动提供。闭环现可产出真实告警。
- 🟢 **`smart-parking-ai` 已真实接入**：百度车牌识别（`BaiduPlateService` 调 `aip.baidubce.com/rest/2.0/ocr/v1/license_plate`，AK/SK 未配置或异常降级 mock）；豆包智能客服（`DoubaoChatService` 调火山方舟 `ark.cn-beijing.volces.com/api/v3/chat/completions`，`model` 字段必须填推理接入点 ID `ep-xxxx` 而非模型名，Key/接入点缺失或调用异常降级本地关键词 mock）。`ai.chat.doubao-endpoint-id` 与 `ai.plate.baidu-api-key/secret-key` 缺省为空，默认走 mock。
- 🟢 **Flyway 数据库迁移已落地（P2）**：8 个数据模块（user/parking/vehicle/order/payment/notify/device/stats）各自持有 `src/main/resources/db/migration/V1__init.sql`（建表，从 `mapper/init.sql` 按模块 owns 拆分）+ `V2__seed.sql`（种子数据），pom 注入 `flyway-core`+`flyway-mysql`，`spring.flyway` 默认 `enabled: false`（CI/本地无 DB 不失败），per-module 自治（`table: schema_history_<模块>`、`baseline-on-migrate: true`），生产在 prod profile / Nacos / K8s common.yml 开启。`mapper/init.sql`+`data.sql` 保留为全量单库参考。
- 🟢 **跨服务 /internal 契约测试已落地（P2）**：8 个 provider 模块各有 `*InternalApiContractTest`（standalone MockMvc 范式，`MockitoExtension` + `standaloneSetup` + 挂 `GlobalExceptionHandler`，按消费者真实传参断言 Result 契约与 service 入参透传）；契约唯一权威文档为 `docs/internal-api-contracts.md`（26 个 `/internal` 端点总表），改任何一侧先改文档再同步测试。
- 🟢 **K8s 部署清单已落地（P2）**：`k8s/` 目录含 12 微服务 Deployment+Service、Ingress（gateway 对外）、ConfigMap（外部基建地址 / 服务间 URL / Flyway 开启）、Secret（MySQL 密码）；基建外部托管不编排；依赖顺序用 initContainer（`nc -z`）保证 user 先启动（跨模块外键以 user 为根）；探针统一 TCP（多数模块未接 actuator，后续可升级 `/actuator/health`）。
- 🟢 **actuator 全覆盖已落地**：10 个微服务全部接入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`（此前仅 6 个），`management.endpoints.web.exposure.include: health,info,prometheus` 全模块统一；新增业务埋点：user（`user_login_success_total`/`user_login_fail_total`/`user_register_total`）、vehicle（`vehicle_bind_total`/`vehicle_total`）、notify（`notification_sent_total{type}`/`notification_broadcast_total`）、ai（`ai_real_call_total{type}`/`ai_mock_fallback_total{type}`，mock 降级率可直接观测）；K8s 探针已从 TCP 升级为 `httpGet /actuator/health/liveness`（不查外部依赖，防误杀）+ `/readiness`（查 DB/Redis/MQ/Nacos，未就绪不接流量），各模块 `probes.enabled=true`；`prometheus/prometheus.yml` 抓取 job 补齐至 10 个服务。
- 🟠 **可观测性剩余缺口**：仅剩**分布式追踪**（Micrometer Tracing + Zipkin/Tempo，未接入）；`docker-compose` 只编排基础设施（MySQL/Redis/Nacos/RabbitMQ/Prometheus/Grafana）未编排微服务本身（本地起服务仍需逐个手动启动）。
- 🟢 **E2E 测试报告（2026-10-28）P0/P1 后端项已修复**：P0——网关 `/api/**/internal/**` 移出白名单改 `X-Internal-Token` 校验（业主不可再直接退款/伪造支付）、`ADMIN_ONLY_PATTERNS` 补 `/notify/admin/**`+车位写操作+反馈管理、订单列表/入场返回车牌解密（`forEach` 方法引用 bug）、支付金额以订单为准（0 元单不生成流水）、支付超时独立 `PAY_STATUS_CLOSED(3)`；P1——`entryByPlateNo` 改 `NOT_SUPPORTED` 传播（修 rollback-only 异常）、`parking/internal/occupancy-summary` 新接口回填占用率、预约 2h 上限+时间下界下沉后端、订单详情/calculate/bill 归属校验、通知接口强制 `X-User-Id`；P2——`Result.fail(ErrorCode)` 重载+业务错误码枚举化（含 invite 6 个新错误码）、`realName` 去脱敏（防回写污染）、计费规则对外只读接口 `GET /api/order/billing-rules`、审计 username 空值回查补齐、网关 401/403 补 `timestamp`。
- 🟢 **E2E 报告前端项已修复（2026-10-28）**：① 区域下拉改为由 `/api/parking/all` 真实数据派生（车位管理 / 车位预约，D 区恢复可见，不再写死 A/B/C）；② 访客邀请改用「status + expireTime」的生效状态统一渲染与筛选（补「已失效」档，过期行不再显示"有效"）；③ 补 `/:pathMatch(.*)*` 404 兜底页 `views/error/NotFound.vue`（移动端不跳 H5、不套浅色主题）+ H5 路由未知地址回首页；④ 欢迎页 / 登录页删除「486 车位 / 68.5% 利用率 / ¥2,847 收入」等硬编码假数据，改为不含数值的能力描述；⑤ H5「我的」用 `/user/community/list` 把 communityId 翻译成真实小区名（缺省按网关口径回退 1 号小区）；⑥ 两端 JWT 解析改为 base64url 还原 + `=` 补齐 + UTF-8 解码，替代裸 `atob`；⑦ 0 元订单不再展示"去支付"（PC 我的订单 / 订单管理 + H5 订单，含 `handleConfirmPay` 拦截），并按 `payStatus=3 已关闭` 修正 PC 订单 / 缴费页状态映射；⑧ 5 个无 UI 入口的封装全部接线——`getSpaceDetail`（车位详情抽屉）、`getDeviceDetail`（设备详情抽屉）、`recognizePlate`（绑定前"识别校验"）、`getAvailableSpaces`（预约页"仅看空闲"）、`sendNotify`→`sendAdminNotify`（通知群发，替换视图内硬编码 URL）。附带：缴费页新增只读「计费规则」卡片（`GET /api/order/billing-rules`）；新增 `utils/plate.js` 车牌密文兜底（密文一律回退脱敏值 / "车牌未同步"，dashboard、订单、H5 均已接入）；脱敏姓名（"张*"）不再回填输入框、留空不下发，避免回写污染真名；`utils/request.js` 支持 `config.silent` 静默失败，辅助查询不弹全局错误。
- 🟢 **前端功能测试报告（2026-10-29）13 项缺陷已修复（2026-10-29）**：逐项说明见 `docs/前端功能测试报告-2026-10-29.md` §0「修复进度」。**P0 DEF-01**——`common-core` 新增 `JacksonConfig` + `LongToStringSerializer`：只把超出 JS 安全整数（2^53-1）的 Long 转字符串，`total/currentParked` 等计数仍是数字，分页与统计逻辑不受影响；车位/设备控制层 `updateById/removeById` 影响 0 行不再返回 200，改回 `SPACE_NOT_FOUND/DEVICE_NOT_FOUND`。实测新建车位返回 `"id":"2115742458669846530"`，用该字符串 id 编辑价格 321 生效、删除生效，伪 id 返回 5201（临时实例 18091 验证后已清理，车位总数回到 87）。**P1 DEF-02**——8 个服务的 `GlobalExceptionHandler` 收敛为 `common-core:BaseGlobalExceptionHandler`：`DuplicateKeyException`→409「车位编号已存在，请换一个编号」、`DataIntegrityViolationException`→4001「数据不合法」、`@Valid` 失败→4001、缺参/类型错→400、`DateTimeParseException`→400「日期格式不正确」，未处理异常只回「系统内部错误」，SQL 与堆栈只进日志；`views/parking/index.vue` 的 `handleSubmit/handleDelete/handleShare` 补 `catch`，共享开关失败回滚，不再产生未捕获 Promise。**P1 DEF-03**——两端 `utils/request.js` 改为共享 refresh Promise 队列：并发 401 全部等同一次刷新后各自重放，刷新失败才统一登出，401 提示 1.5s 节流。**P2**——月租价格三重校验（`@DecimalMin("0.00")` + 表单 validator + 提交兜底），实测接口传 -5 返回 `4001 月租价格不能为负数`；审计 `ip/costTime` 由 `RequestTimingFilter` 记录请求开始时间 + `WebRequestUtils` 采集（X-Forwarded-For/X-Real-IP/remoteAddr），`AuditClient` 在业务线程取值后异步上报（原 `@Async` 会丢 `RequestContextHolder`）；`/stats/dashboard` 的 `currentParked` 改取车位实时占用（实测 23，与「已占用 23」对齐）；`/stats/occupancy` 按日补点（缺失日补 0、今天用实时占用，另返回 `liveOccupied/liveTotal`），平均利用率只统计有统计口径的日期（实测 `avgOccupancy=0.13`）。**P3**——`CODE_MESSAGE` 去掉 5101 硬覆盖，验证码错误能正确提示；前端对 5xx 与含 SQL/堆栈的 message 统一降级为「服务繁忙，请稍后再试」，原文只写控制台；车辆搜索先去掉 `*` 再匹配（输入 `湘A7` 可命中 `湘A****7`），placeholder 改为「搜索车牌，如 湘A****7」；统计页图表加 `graphic` 空态（含「加载失败」）；H5 补 `public/favicon.svg` 并在 `index.html` 声明；统计卡 `avgRate` 修正为 `×100`（原来会把 13% 显示成 0.1%）。**顺带修测试基建**：根 `pom.xml` 统一 `maven-surefire-plugin 3.2.5`——此前 user/notify/device/stats 等模块用 Maven 默认 2.12.4，JUnit 5 用例被静默跳过（`Tests run: 0`），现在 15 个模块全部真实执行、全绿。**复查再收口（同类「写操作假成功」）**：`FeedbackController.reply` 反馈不存在/已被并发删除改返回 `5702`（原直接 `success(null)`）、空回复返回 `4001`，`updateById` 影响 0 行不再当成功，新增 `FeedbackReplyContractTest` 3 例；`DeviceServiceImpl.updateStatus` 设备编号不存在改抛 `5601`（原静默 200，心跳仍按幂等静默处理）。**运行态验收（2026-11-06）**：4 容器 + 10 服务重启后经网关实测 11 个场景全部符合预期（字符串 id 改价生效、伪 id 5201、重复编号 409、负数月租 4001、`currentParked=23` 与车位占用一致、`occupancy` 按日补点 `avgOccupancy=0.26`、审计 `ip/costTime` 非空、反馈回复 5702/4001），明细见报告 §0「运行态验收」；验收中又补一处：未知路径 `NoResourceFoundException` 原被兜底成 `500 系统内部错误`，现映射为 `404 接口不存在：<path>`。
- 🟠 **遗留（不阻塞）**：① 当前 8080-8090 上跑的服务是改动前的 class，需重启（IDEA 里重启全部服务）后修复才在页面生效；② DEF-13 的 8-10 月 E2E 脏数据（¥0.00 支付流水、重复通知/反馈）未清理——清理方案（只读排查 + 带备份的清理 + 回滚 SQL）见 `docs/数据治理-DEF13-脏数据清理.md`，因涉及不可逆删除且需 DB 授权，未擅自执行；③ 订单出场结算/支付全流程、车位预约、退款仍未做端到端验证（见报告 §5）。
