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
| smart-parking-user | 8081 | sys_user, audit_log, community | 用户/权限/审计/小区 |
| smart-parking-parking | 8082 | parking_space, reservation | 车位/预约 |
| smart-parking-vehicle | 8083 | vehicle, monthly_card_order | 车辆/车牌识别/月卡售卖 |
| smart-parking-order | 8084 | parking_order, billing_rule, visitor_invite | 订单/计费/访客邀请 |
| smart-parking-payment | 8085 | payment_record, payment_reconciliation, share_income, invoice | 支付/对账/分润/发票 |
| smart-parking-notify | 8086 | notification, feedback | 通知/用户反馈 |
| smart-parking-device | 8087 | device | 设备/MQTT/道闸 |
| smart-parking-stats | 8088 | parking_stats | 统计（Redis Sorted Set 实时 + MQ 落库） |
| smart-parking-ai | 8090 | — | 车牌识别增强（百度 OCR 真实接入，provider=baidu 需配 AK/SK，缺省降级 mock）/ 智能客服（豆包火山方舟真实接入，provider=doubao 需配 API Key + 推理接入点 ep-xxxx ID，缺省/异常降级 mock） |
| smart-parking-common-* | — | — | core(Result/PageResult/AES/脱敏/异常) / redis / swagger |
| smart-parking-web（管理端 Vue3+Element Plus） | **3000** | — | PC 管理后台，`vite.config.js` 显式 `server.port` |
| smart-parking-h5（业主端 Vue3+Vant4） | **3001** | — | 手机 H5，`vite.config.js` 显式 `server.port` |

> **前端端口铁律**：两端都覆盖了 Vite 默认的 `5173`，管理端 = **3000**、业主端 = **3001**。文档、E2E 配置（`scripts/e2e/playwright.config.js` 的 `H5_BASE_URL`）一律按 3001 写，**不要再出现 5173**。两端的 `/api` 都由 vite 在电脑侧代理到 `http://127.0.0.1:8080`（必须写 `127.0.0.1` 而非 `localhost`，Node 18+ 会把 localhost 优先解析成 `::1` 导致偶发 500/超时），并在 `proxyReq` 里摘掉 `Origin` 头——否则手机用局域网 IP 访问时，带上的局域网 Origin 会被网关 CORS 白名单拒绝，返回 **403 且响应体为空**。

> **跨服务数据访问铁律**：服务只能通过自己的 Mapper 访问自己 owns 的表；跨服务取数必须调对方的 `/internal/**` 接口（实现可用 `RestTemplate` + `InternalHttp` 统一注入 `X-Internal-Token`，也可用 OpenFeign），网关不对外暴露 `/internal/**`。**已发现的唯一违反项**：`stats` 的 `ParkingStatsMapper.xml#selectEntryCountByHour` 直查 `parking_order`，待改为走 order `/internal`（见 `docs/边界确认-2026-11-20.md` §3）。

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

- 🟢 **测试基线已成型（2026-11-20 复测）**：16 个模块全绿，共 **55 个测试类 / 218 个用例**（common-core 28、gateway 11、user 17、parking 13、vehicle 19、order 49、payment 26、notify 17、device 17、stats 6、ai 15）。11 个业务模块各有 `*InternalApiContractTest` 守 `/internal` 契约；计费测试已从"镜像式"（在测试里重写一份公式）改为**反射直调生产 `calculateBillingAmount`**。日封顶口径已按需求方确认改为**自然日**（00:00 分界，逐日重置额度、逐日判周末费率），回归用例锁定：24h30m 跨日 = 60.00（非封顶 200）、20h 跨两天封顶 30 = 40.00、48h 封顶 30 = 80.00、跨周末 48h = 220.00。支付宝沙箱接入后新增两组闸门测试：`AlipaySettleAmountGuardTest`（渠道金额与应付不一致一律拒绝入账，5 例）、`GateSandboxTest`（道闸指令签名/ACK 回执/厂商回落，6 例）。仍缺：JaCoCo 覆盖率硬门槛、`@SpringBootTest` 全链路集成测试。
- 🟢 **支付/设备渠道已沙箱化（2026-11-19）**：
  - **支付宝走真实沙箱**：`alipay-sdk-java:4.40.560.ALL` + 沙箱网关，`AlipaySandboxService` 提供 precreate / page-pay / wap-pay / trade.query / trade.refund / notify 验签；`AlipaySandboxController` 暴露 `POST /api/payment/alipay/{precreate,cashier,query}` 与 `POST /api/payment/callback/alipay/notify`（后者已在网关白名单内，靠验签+app_id+金额三重校验）。`settleFromChannel` 强制渠道金额与本地应付一致（`compareTo`），不一致拒绝入账。`smart-parking.alipay.enabled=true` 时模拟端点 `/alipay/pay` 自动关闭，避免并存绕过。**配置变量**：`SP_ALIPAY_ENABLED / SP_ALIPAY_APP_ID / SP_ALIPAY_PRIVATE_KEY / SP_ALIPAY_PUBLIC_KEY`（注意 `alipay-public-key` 是支付宝公钥，不是应用公钥）。本地无公网回调时用 `alipay/query` 主动查询闭环。**微信无公开沙箱**，保持模拟。
  - **设备厂商通道可插拔**：`device` 新增 `vendor` 列（`V4__device_vendor.sql`，默认 `sandbox`）+ `GateAdapter` 抽象 + `GateAdapterFactory` 按设备厂商路由。内置 `SandboxGateAdapter`（JSON 指令 + `sign=MD5(cmd|deviceNo|seq|salt)` + `/ack` 回执等待）与 `MqttPlainGateAdapter`（兼容老版纯文本 OPEN/CLOSE）；`SandboxGateSimulator` 是虚拟道闸，校验签名后回执，无硬件也能跑通"下发→执行→回执"。接入真实厂商只需新增一个 `GateAdapter` Bean 并改 `device.vendor`，业务代码零改动。**开关**：`smart-parking.device.gate-vendor`(默认 sandbox)、`gate-ack-timeout-ms`(1500)、`strict-ack`(默认 false —— 大多数道闸不回 ACK，严格模式会堵车)、`simulator.enabled`(默认 true)。指令流水：`GET /api/device/gate/command-log`。
  - 顺带修掉设备域两个老问题：Paho 改用 `MemoryPersistence`（不再残留 `*-tcplocalhost18830/` 目录）；`MqttTransport` 开启 `automaticReconnect` 并在重连后恢复订阅（原来只在启动连一次，broker 重启即永久失联）。
  - 详见 `docs/沙箱渠道接入-支付宝与设备-2026-11-19.md`。
  - **支付 → 厂商通道已接通（2026-11-19）**：`PaymentRecordServiceImpl#paymentSuccess` 在**本地事务提交后**异步调 `POST /api/device/internal/gate/release`（`X-Internal-Token`，非管理员 JWT）；device 侧 `releaseForPayment` 优先挑在线「出口」道闸、经 `GateAdapter` 下发 OPEN，回执/seq/耗时进 `command-log`。三条铁律：提交后才下发（否则支付回滚=免费放行）、异步（ACK 最长等 1.5s，不能拖慢 `/alipay/query`）、失败只记日志不外抛（钱已收）。开关 `smart-parking.gate.release-on-payment`（默认 true，置 false 只入账不动闸）。单测 `ReleaseGatePickerTest`(5 例) 锁选闸逻辑，`DeviceInternalApiContractTest` 补 2 例契约。
  - **出场分级放行 + 欠费治理（2026-11-20）**：`deviceExit` 结算后按**生效信用额度**分级放行 —— 已支付/免费/额度内放行（先出场后付费，出口不堵），超额拦停并返回 `needPay` + `payTip`；**生效额度 = max(配置兜底值, 该规则单日封顶=取平峰/周末较大者)**（`effectiveCreditLimit`）。口径按**小区场景**定：停车时长以天计，额度跟单日封顶走，否则业主回家停一晚就被拦；配置值只在规则无封顶（月租）时生效。金额在结算时锁定，付款后由支付成功事件抬杆，不会重复计费。`ArrearageScanTask`（每 10 分钟）：出场满 24h 未支付发一次催缴（`remind_time` 去重）、满 48h 标记 `arrear_status=1`，欠费次数 > N（默认 3）或累计 > M（默认 100 元）时置 `vehicle.status=1` 拉黑，入场由车辆服务 `recognize` 拦停（`deviceEntry` 透传拦截原因，不再当"未注册车辆"走访客白名单）。判定逻辑抽成纯函数 `canRelease` / `effectiveCreditLimit` / `exceedsLimit`，单测 `GateReleasePolicyTest`(9 例)、`ArrearagePolicyTest`(4 例)。**只加两列，不建欠费表**：欠费本质是"已完成且未支付"的订单视图。
  - **支付回跳自愈（2026-11-20）**：收银台 `return_url` 携带 `payNo`，订单页挂载时补查一次到账（3 次重试，沙箱网关会偶发空响应）。解决本地 `notify` 不可达时"付了钱、页面仍显示待支付"。
  - **仍存缺口**：异步 `notify` 在 localhost 不可达，闭环实际靠 `alipay/query` 轮询（生产公网部署后自然恢复）。
- 🟢 **项目边界：只做车位租赁，不做产权（2026-11-20 需求方确认）**：系统按**人防车位 + 公摊车位**运营 —— 车位是"可租资源"，收入为月租 / 临停 / 共享分成，**不涉及产权车位销售、产权登记与"车位管理费"账单**。因此**不新增 `ownership_type` 字段**、不区分产权/人防/公摊；"月租车停自己车位 0 元"的含义是租金已含在月卡（`space.owner_id` 是承租关系，不是产权归属）。若某小区确有产权车位且只收管理费，由物业核一个低价月租表达即可，无需改代码。已知边界：`space.owner_id == vehicle.owner_id` 的判定在"家人车辆登记在不同人名下、停同一承租车位"时会按临停计费，不处理。**讨论相关需求时不要重新引入产权模型**。
- 🟢 **全项目边界已核查（2026-11-20，详见 `docs/边界确认-2026-11-20.md`）**：骨架边界成立 —— 无跨服务写表、`/internal/**` 网关 + `InternalApiGuardFilter` 双校验（31 端点与 `docs/internal-api-contracts.md` 一致）、网关白名单仅 6 条、服务依赖无环。**两处缺口已于当日收口**：
  - ① **月卡已接入支付服务**：`payment_record` 新增 `biz_type`（1停车 2月卡）/ `biz_id`（Flyway `V6__payment_biz_type.sql`），`order_id` 改为可空（外键对 NULL 不校验，历史流水回填 `biz_id=order_id`）。链路：H5 下单（vehicle）→ `POST /api/payment/monthly/create?monthlyOrderId=&payType=`（金额/归属以月卡订单为准，业主只能为自己的月卡付款）→ 收银台或模拟支付 → `paymentSuccess` 按 `bizType=2` 分派 → `POST /api/vehicle/internal/monthly/mark-paid` 幂等续期。**vehicle 的 `POST /monthly/pay` 自助通道已删除**（此前自造 `MOCK-` 流水、不记账）。月卡收入现进入 `payment_record`，`/stats/income`、`/payment/internal/income-summary`、对账自动覆盖。**月卡续期失败必须抛错**（与开闸相反）：本地流水回滚为待支付，靠渠道 query/回调重试自愈；发票仍只认停车订单（月卡发票不支持）。退款方向：月卡流水 `order_id` 为 NULL，只改本表状态并原路退回，不回滚已生效的月卡权益（人工处理）。
  - ② **stats 不再直查 `parking_order`**：`getPeakHours()` 改调 `GET /api/order/internal/entry-count-by-hour?date=`，`ParkingStatsMapper.xml#selectEntryCountByHour` 已删除。
  - ③ **`strict-secrets` 已扩容为「生产模式总闸」**：`common-core` 的 `SecuritySecretsConfig` 现校验 JWT / AES / **内部令牌**三类（内部令牌等于内置默认值或为空即拒绝启动）；payment 的 `PaymentStrictModeGuard` 单独校验 **回调密钥非默认值** 与 **`mock-enabled=false`**（这两项只有 payment 有，放进公共模块会让其他服务的默认值反过来拦住自己）。默认值统一收口到 `Constants.DEFAULT_INTERNAL_TOKEN` / `DEFAULT_CALLBACK_SECRET`，`InternalApiGuardConfig`、`SecuritySecretsConfig`、`PaymentRecordServiceImpl` 三处的 `@Value` 占位符必须完全一致（同样优先 `SP_INTERNAL_TOKEN`）——曾出现过 payment 侧漏了环境变量前缀，只配 env 时守门器用新值、调用方仍用旧默认值，表现为内部接口恒 403。
  - ④ **分润比例已配置化**：`smart-parking.share.owner-ratio`（env `SP_SHARE_OWNER_RATIO`，默认 0.7），合法区间 (0,1)，非法值（含误写成百分数 70）回退默认并打 WARN 而不中断分润；物业占比 = 1 − 该值，拆分时业主金额四舍五入、物业取差额，保证两笔之和精确等于原额。
- 🟢 **Phase D 监控闭环已接线且业务埋点已完成**：`stats` + `gateway/order/payment/parking/device` 五个服务均接入 `actuator`+`micrometer-registry-prometheus` 暴露 `/actuator/prometheus`（带 `application` 标签）；`common-core` 的 `PrometheusMetricSource` 调 Prometheus `/api/v1/query`（无数据返回 `NaN` 不误报）；`MonitorAutoConfiguration` 周期驱动 `MonitorLoop` 评估 `bands.yaml`，越界写 `intent-*.md`；`docker-compose` 已加 `prometheus`+`grafana`。业务埋点：`payment_recon_diff_amount`(PaymentReconMetrics)、`space_cache_miss_rate_pct`(ParkingCacheMetrics)、`device_offline_rate_pct`(DeviceOnlineMetrics)、`mq_order_backlog`/`mysql_active_connections`(OrderBusinessMetrics) 四个手写 Gauge，HTTP 延迟/错误率由 Actuator 自动提供。闭环现可产出真实告警。
- 🟢 **`smart-parking-ai` 已真实接入**：百度车牌识别（`BaiduPlateService` 调 `aip.baidubce.com/rest/2.0/ocr/v1/license_plate`，AK/SK 未配置或异常降级 mock）；豆包智能客服（`DoubaoChatService` 调火山方舟 `ark.cn-beijing.volces.com/api/v3/chat/completions`，`model` 字段必须填推理接入点 ID `ep-xxxx` 而非模型名，Key/接入点缺失或调用异常降级本地关键词 mock）。`ai.chat.doubao-endpoint-id` 与 `ai.plate.baidu-api-key/secret-key` 缺省为空，默认走 mock。
- 🟢 **Flyway 数据库迁移已落地并默认开启（P2）**：8 个数据模块（user/parking/vehicle/order/payment/notify/device/stats）各自持有 `src/main/resources/db/migration/V1__init.sql`（建表，从 `mapper/init.sql` 按模块 owns 拆分）+ `V2__seed.sql`（种子，`INSERT IGNORE` 幂等）+ `V3__soft_delete_unique_key.sql`（软删除唯一键，7 张表按模块归属拆分，纯 SQL 幂等写法不依赖 `DELIMITER`）。pom 注入 `flyway-core`+`flyway-mysql`；`spring.flyway.enabled: ${SP_FLYWAY_ENABLED:true}` 默认开启，`baseline-version: "0"`（**已从 "2" 下调，重要**）——8 个模块共用同一 `smart_parking` schema，只要任一模块先建了表，schema 对后续模块就已"非空"；此时若基线是 V2，后续模块会**跳过自己的 V1/V2**，表永远建不出来（实测 payment 启动即 `Table 'smart_parking.payment_record' doesn't exist`）。下调为 0 后，新库与"仅其它模块已建表"的共享库都能从 V1 全量执行；配套把 8 个 V1 的建表语句统一改为 `CREATE TABLE IF NOT EXISTS`、两处补列改成 `information_schema` 幂等判断、V2 种子本来就是 `INSERT IGNORE`，因此重复执行安全。**模块启动顺序有硬依赖**（跨模块物理外键）：`user → parking → vehicle → order → payment`，`notify/device/stats` 只依赖 user，可与 payment 并行。无 DB 的 CI 用 `SP_FLYWAY_ENABLED=false` 关闭。`mapper/init.sql`+`data.sql` 保留为全量单库参考，手工脚本 `mapper/migration/2026-11-16_soft_delete_unique_key.sql` 保留给无法升级服务的存量环境。
- 🟢 **跨服务 /internal 契约测试已落地（P2）**：8 个 provider 模块各有 `*InternalApiContractTest`（standalone MockMvc 范式，`MockitoExtension` + `standaloneSetup` + 挂 `GlobalExceptionHandler`，按消费者真实传参断言 Result 契约与 service 入参透传）；契约唯一权威文档为 `docs/internal-api-contracts.md`（27 个 `/internal` 端点总表），改任何一侧先改文档再同步测试。
- 🟢 **K8s 部署清单已落地（P2）**：`k8s/` 目录含 12 微服务 Deployment+Service、Ingress（gateway 对外）、ConfigMap（外部基建地址 / 服务间 URL / Flyway 开启）、Secret（MySQL 密码）；基建外部托管不编排；依赖顺序用 initContainer（`nc -z`）保证 user 先启动（跨模块外键以 user 为根）；探针统一 TCP（多数模块未接 actuator，后续可升级 `/actuator/health`）。
- 🟢 **actuator 全覆盖已落地**：10 个微服务全部接入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`（此前仅 6 个），`management.endpoints.web.exposure.include: health,info,prometheus` 全模块统一；新增业务埋点：user（`user_login_success_total`/`user_login_fail_total`/`user_register_total`）、vehicle（`vehicle_bind_total`/`vehicle_total`）、notify（`notification_sent_total{type}`/`notification_broadcast_total`）、ai（`ai_real_call_total{type}`/`ai_mock_fallback_total{type}`，mock 降级率可直接观测）；K8s 探针已从 TCP 升级为 `httpGet /actuator/health/liveness`（不查外部依赖，防误杀）+ `/readiness`（查 DB/Redis/MQ/Nacos，未就绪不接流量），各模块 `probes.enabled=true`；`prometheus/prometheus.yml` 抓取 job 补齐至 10 个服务。
- 🟠 **可观测性剩余缺口**：仅剩**分布式追踪**（Micrometer Tracing + Zipkin/Tempo，未接入）；`docker-compose` 只编排基础设施（MySQL/Redis/Nacos/RabbitMQ/Prometheus/Grafana）未编排微服务本身（本地起服务仍需逐个手动启动）。
- 🟢 **E2E 测试报告（2026-10-28）P0/P1 后端项已修复**：P0——网关 `/api/**/internal/**` 移出白名单改 `X-Internal-Token` 校验（业主不可再直接退款/伪造支付）、`ADMIN_ONLY_PATTERNS` 补 `/notify/admin/**`+车位写操作+反馈管理、订单列表/入场返回车牌解密（`forEach` 方法引用 bug）、支付金额以订单为准（0 元单不生成流水）、支付超时独立 `PAY_STATUS_CLOSED(3)`；P1——`entryByPlateNo` 改 `NOT_SUPPORTED` 传播（修 rollback-only 异常）、`parking/internal/occupancy-summary` 新接口回填占用率、预约 2h 上限+时间下界下沉后端、订单详情/calculate/bill 归属校验、通知接口强制 `X-User-Id`；P2——`Result.fail(ErrorCode)` 重载+业务错误码枚举化（含 invite 6 个新错误码）、`realName` 去脱敏（防回写污染）、计费规则对外只读接口 `GET /api/order/billing-rules`、审计 username 空值回查补齐、网关 401/403 补 `timestamp`。
- 🟢 **E2E 报告前端 8 项已全部落地（2026-10-28 完成，2026-11-17 随分支提交）**：① P1-7 区域筛选改 `areaOptions` + `mergeAreas` 数据驱动（从 `/parking/all` 聚合，补 D 区）；② P1-8 邀请过期判定（PC `isExpired`/`effStatus`，H5 于 2026-11-17 补齐同口径）；③ P1-9 404 兜底（`/:pathMatch(.*)*` 必须置于 routes 末尾，否则整页白屏）；④ P2-3 登录/欢迎页硬编码统计换成「实时/无感/闭环/联动」能力描述；⑤ P2-4 H5 小区名按 `communityId` 查 `/community/list` 回填；⑥ P2-9 `decodeJwtPayload` 支持 base64url 并用 `TextDecoder` 解中文；⑦ P2-10 0 元订单统一 `isFree` 归入已完成、隐藏「去支付」并前置拦截；⑧ P2-8 五个无 UI 入口 API 全部接线（车位详情、设备详情、车牌识别校验、仅空闲筛选、群发通知）。同批补 H5 首屏骨架屏与卡片按压态（Orders / Vehicles / Invite / Mine），消除"先闪空态再跳列表"的跳版。
- 🟢 **前端功能测试（2026-11-16）13 项缺陷已修复并运行态验收**（详见 `docs/前端功能测试报告-2026-11-16.md` §0）：
  - **P1**：① 预约预填时间改为「向上取整到下一分钟」，后端新增 60 秒时钟容差（`ParkingSpaceServiceImpl#reserveSpace` 的 `RESERVE_TIME_TOLERANCE_SECONDS`），两端同一把尺；② 新增 `ErrorCode.RESERVE_TIME_INVALID(5206)` / `RESERVATION_LIMIT_EXCEEDED(5207)` 替代复用 `SPACE_UNAVAILABLE`，`request.js` 的链路在错误对象上打 `__notified` 标记并导出 `toastOnce(err, fallback)`，13 个页面 / H5 6 处改为调用它，同一次失败只弹一条。
  - **P2**：③ 车位页新增 `filteredSpaces` 计算属性，地图与统计卡跟随区域/类型/状态筛选（接口 `/parking/all` 只支持区域，其余前端二次过滤）；④ 7 张含 `is_deleted` 的表改用「仅未删除行有值」的生成列 + 唯一索引（`alive_*`），迁移脚本 `mapper/migration/2026-11-16_soft_delete_unique_key.sql`（幂等，已执行），`mapper/init.sql` 同步；⑤ `safeMessage` 改为「后端干净文案优先 → 字典兜底 → 通用兜底」，401/403/429 仍固定文案；⑥ 删除统计页手写 `sp-legend`，只保留 ECharts legend；⑦ 首页保留全量车牌，展示前 3 个并标注「等 N 辆」。
  - **P3**：⑧ 未读数迁到 `store/notify.js`（Pinia），通知页标记已读后 `decrement + refresh` 即时同步角标；⑨ `handleShare` 按 id 回写 `tableData`/`allSpaces`/`current`；⑩ 缴费订单ID列 190px + `show-overflow-tooltip`、审计 IP 列 160px + 等宽字体，行高 41px 不折行；⑪ `WebRequestUtils#normalize` 对 `X-Forwarded-For`/`X-Real-IP`/`remoteAddr` 统一归一（`::1`、`0:0:0:0:0:0:0:1`、`::ffff:127.0.0.1` → `127.0.0.1`），审计页 `fmtIp` 兜住历史数据；⑫ 小区卡片拆成「实际车位（按 `communityId` 实时查 `/parking/spaces`）+ 规划 N」，车位总量统计卡用实际值合计（47+20+20=87）；⑬ 收入全 0 时复用 `emptyGraphic` 显示「当前区间暂无收入数据」。
  - **遗留项已全部闭环（2026-11-16 修复）**：
    ① **Flyway 已默认开启**，D4 的 DDL 以 `V3__soft_delete_unique_key.sql` 进入 7 个模块的 `db/migration`，服务启动自动执行；实测 8 个模块的 `schema_history_*` 均为 `baseline 2 → V3 OK`，重复执行幂等，`ParkingSpace` 三轮「创建-删除-重建同编号」全部 200。
    ② **DEF-13 脏数据已清理**（`scripts/data-fix/DEF13-dirty-data-20261116.sql`，含备份/回滚）：0 元成功流水软删 1 条、重复通知软删 15 条、重复反馈物理删 1 条（保留最早），清理后三类校验均为 0；4 条 `pay_status=2` 的 0 元未成功流水按口径保留。备份表 `bak_20261116_*` 保留用于回滚。
    ③ **E2E 已固化到 `scripts/e2e/`（Playwright）**，11 条用例覆盖：订单出场结算→支付→退款全链路、已支付订单重复支付拦截、预约→取消→重复取消、预约时间边界（5206）、软删编号复用（D4）、管理端登录/车位表格/状态筛选、H5 登录与横向溢出；`npm run test:api|test:pc|test:h5`，复用本机 Edge 无需下载浏览器。
  - **顺带发现并修复的 P0 缺陷**：`payment` 与 `notify` 经网关调用 `/api/**/internal/**` 时未带 `X-Internal-Token`，被网关 403 拦截 —— 导致**任意非 0 元订单创建支付流水恒返回 5402「订单服务暂不可用」**（0 元单不生成流水，此前未暴露）。修复：`PaymentRecordServiceImpl`/`NotificationController` 的 `RestTemplate` 增加拦截器注入内部令牌（`Constants.INTERNAL_TOKEN_HEADER`），令牌值 `smart-parking.internal-token` 缺省 `SmartParking-Internal-2026`，生产用 `SP_INTERNAL_TOKEN` 统一覆盖。修复后实测创建流水/支付/退款均 200，E2E 用例守护。
- 🟢 **双份 H5 已收敛为单一业主端（2026-11-18，验收 D-09 方案 A）**：`smart-parking-web/src/h5/*`（6 文件 / 292 行）是早期原型——初始化提交后**零改动**，`profile.vue` 的「添加车辆 / 生成邀请码」直接 `showToast('请在管理端 Web ...')`，`confirm-plate.vue` 调 `/order/internal/confirm-plate` 在 P0-1 加固后已被网关 403 拦死。已整体删除（连同 `/h5` 路由与 `web` 的 `vant` 依赖）。移动 UA 改落 `/mobile-guide` 引导页：展示 `VITE_H5_BASE_URL` 指向的业主端地址、提供跳转与「仍要使用管理端」的 `sessionStorage` 一次性放行（带 `?to=` 回跳原目标）。业主端唯一实现为 `smart-parking-h5`（3001）。注意：车牌人工确认的正规入口一直是管理端「设备管理」页的**抓拍联动 / 出口放行**，与已删除的旧 H5 无关。
- 🟢 **业主端已补齐「后端有、前端无」5 项（2026-11-18，验收 D-01~D-05）**：H5 新增 `/payments`（支付记录：状态筛选 + 累计支付）、`/feedback`（四类反馈提交 + 我的反馈 + 物业回复）、`/orders/:orderNo`（订单详情：出入场/时长/费用明细按 `billingRuleId` 映射规则，在场可取消、待缴可支付）、`/profile-edit`（头像上传 + 姓名/手机号），月卡页底部补充值记录。同时修掉两个后端缺陷：① `FeedbackController` 的提交与 `/my` 查询直接信任前端 `userId`——可替他人投诉、可遍历他人反馈（IDOR），改为 `role=0` 时以 `X-User-Id` 覆盖并补内容校验；② 头像上传返回 `/uploads/...`，但网关无该路由、服务也无静态资源映射，**上传成功却恒为裂图**，改为 `/api/user/avatar/{uuid}.png` 由新接口回读（文件名正则白名单挡目录穿越），并加入网关 `WHITE_LIST`（浏览器 `<img>` 不携带 JWT）；`upload-avatar` 同时补扩展名白名单与 5MB multipart 上限。测试：gateway 11 / user 14 / notify 17 单测，E2E 新增 `h5-owner-more`(9) 与 `owner-profile.api`(7)。
- 🟢 **`docs/AGENTS.md` 旧副本已删除（2026-11-18）**：与根 `AGENTS.md` 同结构但停在 2026-11-06，内容已明显分叉，留着会让 AI 读到过期规范；规范唯一入口是根 `AGENTS.md`。
- ⚠️ **E2E 里写 SQL 的两个硬约束（2026-11-18 实测踩坑）**：`tests/support/api.js` 的 `sql()` 经 `execSync` → `cmd.exe` 执行，① **中文不能进 SQL**：参数与输出都按 ANSI 代码页转换，`SELECT real_name` 读回 `张三` 得到 `??`，再写回就把姓名改成 `??`（已发生过一次，靠 `HEX()` 还原）；需要中文一律 `SELECT HEX(col)` 读、`CONVERT(0x... USING utf8mb4)` 写。② **语句必须单行**：多行 SQL 会在第一个换行处被截断，静默返回多条/错误结果（曾表现为「订单详情恒 5301」）。
