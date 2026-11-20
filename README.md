# 小区智慧停车管理系统

基于 Spring Cloud 微服务架构的小区智慧停车管理系统。

## 技术栈

* **后端**：Spring Boot 3.2.4 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0

* **前端**：Vue 3 + Element Plus + Vite

* **数据库**：MySQL 8.0（单库） + Redis 7.x

* **消息队列**：RabbitMQ 3.13

* **注册中心**：Nacos 2.3.1

* **容器化**：Docker + Docker Compose

## 快速开始

### 1. 启动基础设施服务

```bash
# 启动 MySQL、Redis、Nacos、RabbitMQ
docker-compose up -d
```

### 2. 导入数据库

**方式 A（推荐）：交给 Flyway 自动建库。** 只需保证 MySQL 里存在空的 `smart_parking` 库，启动服务时各模块会按自己的 `db/migration` 版本脚本建表 + 灌种子（幂等，可重复启动）。**必须按依赖顺序启动**，否则跨模块外键会失败：

```
user → parking → vehicle → order → payment
（notify / device / stats 只依赖 user，可与 payment 并行）
```

**方式 B：手工导入。** 在 Navicat 中先执行 `mapper/init.sql`（创建库表结构）。注意 `mapper/init.sql` 只建表不灌种子，种子数据由各模块的 `V2__seed.sql` 负责，所以手工导入后也要正常启动服务让 Flyway 跑增量。

### 3. 启动 Nacos 配置

访问 <http://localhost:8849/nacos，默认账号密码：nacos/nacos>

### 4. 编译项目

```bash
mvn clean install -DskipTests
```

### 5. 启动服务（按顺序）

1. `smart-parking-gateway` - 网关服务 (8080)
2. `smart-parking-user` - 用户服务 (8081)
3. 按上文依赖顺序启动其余微服务（parking 8082 → vehicle 8083 → order 8084 → payment 8085 → notify 8086 → device 8087 → stats 8088 → ai 8090）

### 5.1 生产部署必须覆盖的密钥（重要）

以下配置项都带**默认值以方便本地开发**，但生产环境必须用环境变量覆盖，否则等同没有防护：

| 环境变量 | 作用 | 默认值 |
| --- | --- | --- |
| `SMART_PARKING_JWT_SECRET` | 网关 JWT 签名密钥（≥32 字节） | 内置开发密钥 |
| `SMART_PARKING_AES_KEY` | 车牌/手机号 AES 加密密钥（32 字节） | 内置开发密钥 |
| `SP_INTERNAL_TOKEN` | 服务间 `/internal/**` 调用令牌 | `SmartParking-Internal-2026` |
| `SP_CALLBACK_SECRET` | 支付回调验签密钥，`sign = MD5(payNo + "&" + secret)` | `SmartParkingCallback2026` |
| `SP_MOCK_PAY_ENABLED` | 模拟支付开关，接真实渠道后必须置 `false` | `true` |
| `SP_GATE_SIGN_SALT` | 道闸指令签名盐，`sign = MD5(cmd\|deviceNo\|seq\|salt)` | `SmartParkingGateSandbox` |

设 `smart-parking.strict-secrets=true` 即为**生产模式总闸**：上述 6 项任一仍是默认值/空值，服务直接拒绝启动（模拟支付则必须显式置 `false`）。校验分三处——`SecuritySecretsConfig`（公共模块）管 JWT / AES / 内部令牌三项全服务通用的；`PaymentStrictModeGuard`（payment 专属）管回调密钥与 mock 开关；`DeviceStrictModeGuard`（device 专属）管道闸签名盐。后两项只有对应服务才有这些配置，放进公共模块会误伤其他服务。

> **道闸签名盐为什么要单独拎出来**：盐是 `MD5(cmd|deviceNo|seq|salt)` 里唯一的秘密。默认盐写在 `Constants.DEFAULT_GATE_SIGN_SALT` 里、算法也是公开的，拿着设备号和序号就能拼出一条 payload 让闸机抬杆。它原来还是 `GateSigner` 里的 `private static final String`，接真实厂商要么改代码、要么要求厂商接受自研算法；现在换成环境变量即可。strict 模式下仍用默认盐会拒绝启动，非 strict 只 WARN（本地演示没有真实硬件，默认盐是开箱可用的前提）。

> `SP_INTERNAL_TOKEN` 必须在**所有服务和网关**上取同一个值。三处读取它的地方（`InternalApiGuardConfig` 守门器、`SecuritySecretsConfig` 校验、`PaymentRecordServiceImpl` 直连调用）占位符表达式已对齐，统一优先读环境变量。

共享车位分润比例由 `SP_SHARE_OWNER_RATIO`（默认 `0.7`，业主占比）控制，物业占比为 `1 - 该值`；取值必须落在 (0,1)，配置成 `70` 这类百分数会被识别为非法并回退默认值。

### 6. 启动前端

两端都是 Vite 工程，**端口固定不可互换**，请先起网关 8080（`/api/**` 由 vite 服务端代理转发，前端本身不跨域）。

**管理端（PC）—— 3000**

```bash
cd smart-parking-web
npm install
npm run dev
```

访问 <http://localhost:3000>，默认账号：admin / admin123

**业主端（H5）—— 3001**

```bash
cd smart-parking-h5
npm install
npm run dev
```

访问 <http://localhost:3001>，用业主手机号 + 短信验证码登录（开发环境验证码从 Redis `captcha:<key>` 取）。

| 前端 | 端口 | 工程目录 | 配置位置 |
| --- | --- | --- | --- |
| 管理端 | **3000** | `smart-parking-web` | `vite.config.js` → `server.port` |
| 业主端 H5 | **3001** | `smart-parking-h5` | `vite.config.js` → `server.port` |

> **注意 `5173` 是错的**。Vite 的默认端口 5173 在本项目两个工程里都被显式覆盖了（3000 / 3001），旧文档和 `scripts/e2e/playwright.config.js` 里的 5173 默认值会导致 H5 的 UI 用例连不上页面。现已统一改为 3001；跑 E2E 前确认 `netstat -ano | findstr :3001` 有监听。
>
> 另注：Grafana 默认也占 3000，与管理端 dev 端口冲突。同时开时把 Grafana 映射到别的端口（`docker-compose.yml` 里改端口映射），或先把管理端 dev 停掉。

### 7. 访问 API 文档

* Knife4j：<http://localhost:8081/doc.html>

### 8. 手机 / 局域网访问（可选）

手机与电脑在**同一网段**（同一 WiFi 或同一热点）时即可访问，无需改代码、无需端口映射。

**只要做两件事：**

1. **用电脑的局域网 IP，不要用 localhost**

   ```bash
   ipconfig            # 找 WLAN 的 IPv4 地址，例如 10.168.1.156
   ```

   手机浏览器打开 `http://<电脑IP>:3001`（H5）或 `http://<电脑IP>:3000`（管理端）。
   手机上的 `localhost` 指向手机自己，一定打不开。

2. **确认 Windows 防火墙放行 node 的入站**

   首次 `npm run dev` 时系统会弹窗询问，选「允许访问」即可；若误点拒绝，去
   *Windows 安全中心 → 防火墙 → 允许应用通过防火墙* 勾上 Node.js。
   只放行 Node 占用的前端端口即可。

**不需要放行的端口：**

* 后端 8080 / 各微服务端口都不用向局域网开放。H5 请求 `/api/**` 由 vite 在**电脑侧**代理到
  `127.0.0.1:8080`，手机全程只与前端端口通信。
* 不需要 `netsh interface portproxy` 之类的端口映射，同一网段直连即可。

**唯一需要留意的代码层：网关 CORS 白名单**

浏览器对 POST 请求会带上 `Origin: http://<电脑IP>:3001`，网关的 `allowedOriginPatterns`
必须包含该来源，否则会返回 **403 且响应体为空**，前端只能兜底提示「请求失败」。
默认值已包含 `localhost` / `127.0.0.1` 与 `192.168.*` / `10.*` / `172.16.*` 私有网段；
换网络环境时可用环境变量覆盖（逗号分隔）：

```bash
SP_CORS_ORIGINS=http://localhost:3000,http://localhost:3001,http://10.168.1.156:3001
```

> 若手机能连上 WiFi 却访问不到（`ping` 也不通），说明该网络做了客户端隔离（常见于校园网、
> 酒店/公共 WiFi）。此时改用手机热点或内网穿透，与本项目代码无关。

## 项目结构

```
smart-parking-system/
├── smart-parking-common/          # 公共模块
│   ├── smart-parking-common-core/ # 核心工具类
│   ├── smart-parking-common-redis/ # Redis工具
│   └── smart-parking-common-swagger/ # API文档
├── smart-parking-gateway/         # 网关服务
├── smart-parking-user/            # 用户服务
├── smart-parking-parking/         # 车位服务
├── smart-parking-vehicle/         # 车辆服务
├── smart-parking-order/           # 订单服务
├── smart-parking-payment/         # 支付服务
├── smart-parking-notify/          # 通知服务
├── smart-parking-device/          # 设备服务
├── smart-parking-stats/           # 统计服务
├── smart-parking-web/             # 前端管理端
├── docker-compose.yml             # 容器编排
└── mapper/                        # 数据库脚本
```

## 计费口径（小区场景）

本系统面向**住宅小区**，不是商场。两者费率结构根本不同：商场是"短时高频"，30 分钟免费、5 元/小时、50 元封顶尚可；小区的车一停就是半天到一整天，同样的规则下业主回家停一晚 50 元、停一个月 1500 元，远超月卡 300 元 —— 规则本身不可用，用户只会选择逃费或堵门。

当前规则（`billing_rule` 表，管理端「计费规则」页可调）：

| 规则 | 免费时长 | 单价 | 日封顶（自然日 00:00 重置） | 典型场景 |
|---|---|---|---|---|
| 月租车 | — | 300 元/月 | — | 业主承租的固定车位 |
| 临停（业主未办卡 / 外来车） | 30 分钟 | 2.00 元/小时 | **15.00 元** | 临时进出、外来短停 |
| 新能源 | 60 分钟 | 1.50 元/小时 | **15.00 元** | 业主新能源车 |
| 访客 | 60 分钟 | 1.00 元/小时 | **15.00 元** | 探亲过夜 |

三条规则日封顶统一为 15 元，即**不办月卡的业主一个月最多约 450 元**（15 × 30）。与改造前对比：业主停 11 小时 **50.00 → 15.00 元**，访客过夜 18 小时 **60.00 → 15.00 元**，月停 **1500 → 450 元**。

月卡 300 元 / 临停月封顶 450 元，差距只有 150 元 —— 这是有意的：业主按自己的出行频率选，天天停满办卡省 150，出差多、只停十来天的按次付更划算（10 天约 150 元），不必为了"划算"硬买月卡。物业侧两种计费都成立，不靠惩罚性定价逼办卡。

> 口径说明：450 元是"按日封顶推出来的月量级"，不是车辆月度累计硬封顶。一天内多次进出是独立订单，各按当次日封顶计费；如需严格的"单车月度累计封顶"，要新增按月聚合的额度控制（当前未做）。

出场信用额度直接绑定"日封顶"，所以**停一天 / 过一夜总能先出场后付**，只有跨日累积超过一天封顶（多为长期占用、疑似弃车）才要求当场结清 —— 这是小区场景下"出口不堵"与"防逃费"的平衡点。

### 经营边界：只做租赁，不做产权

本项目按**人防车位 + 公摊车位**的运营模式设计 —— 车位是"可出租资源"，收入来自**月租 / 临停 / 共享分成**，不涉及产权车位的销售、产权登记和"车位管理费"账单。

- **不设"车位权属"字段**，不区分产权 / 人防 / 公摊。三类车位在本系统中一律是"可租车位"，差别只体现在月租价格上；假如某小区确有产权车位且只收管理费，由物业核一个低价月租即可，**不需要改代码**。
- **"月租车停自己车位 0 元"的含义是租金已含在月卡里**（`space.owner_id` 表示承租关系，不是产权归属），不是产权豁免。
- 系统只回答"这个车位租给了谁、租到什么时候、欠不欠费"，不回答"这个车位是谁的产权"。

这是有意划定的范围：产权归属与登记属不动产登记系统的职责，停车系统只管**使用权流转与通行秩序**。已知限制：判定"停自己车位"用的是 `space.owner_id == vehicle.owner_id`，若家人车辆登记在不同人名下停同一承租车位，会被按临停计费 —— 当前不处理，属租赁模型下的边界。

## 数据库说明

所有微服务共用一个数据库 `smart_parking`，实际共 **18 张业务表**（另有 8 张 Flyway `schema_history_*` 历史表）。主要表如下：

| 表名              | 说明    |
| --------------- | ----- |
| sys\_user       | 用户表   |
| parking\_space  | 车位表   |
| vehicle         | 车辆表   |
| billing\_rule   | 计费规则表 |
| parking\_order  | 停车订单表 |
| payment\_record | 支付记录表 |
| device          | 设备表   |
| notification    | 通知表   |
| audit\_log      | 操作日志表 |
| parking\_stats  | 统计数据表 |
| community       | 小区表（多小区扩展） |
| reservation     | 车位预约表 |
| share\_income   | 共享停车分润表 |
| feedback        | 用户反馈表 |
| payment\_reconciliation | 支付对账表 |
| monthly\_card\_order | 月卡订单表（预付费套餐单，与按时计费的停车订单分表） |
| invoice         | 发票申请表 |
| visitor\_invite | 访客邀请表 |

## 微服务端口

| 服务                     | 端口   |
| ---------------------- | ---- |
| smart-parking-gateway  | 8080 |
| smart-parking-user     | 8081 |
| smart-parking-parking  | 8082 |
| smart-parking-vehicle  | 8083 |
| smart-parking-order    | 8084 |
| smart-parking-payment  | 8085 |
| smart-parking-notify   | 8086 |
| smart-parking-device   | 8087 |
| smart-parking-stats    | 8088 |
| smart-parking-ai       | 8090 |
| smart-parking-web (管理端) | 3000 |
| smart-parking-h5 (业主端)  | 3001 |

---

## CI 门禁与 AI 原生 SDLC 规范

本项目已落地 **AI 原生 SDLC（工件驱动闭环）** 的完整工程化骨架：规划(`docs/sdlc/intent.md`) → 设计(`spec.md`) → 构建(`AGENTS.md` + `plan.md`) → 测试(16 个模块 / 55 个测试类 / 216 个用例 + 钩子) → 部署(`.github/workflows/ai-review.yml`) → 维护(`bands.yaml` 监控闭环)。

**第三方渠道**：支付宝走**真实沙箱**（官方 `alipay-sdk-java` + 沙箱网关，支持收银台支付/主动查询/原路退款/异步通知验签，切生产只换网关与密钥）；微信支付无公开沙箱，保持模拟通道。设备侧以 `GateAdapter` 抽象厂商协议，内置 `sandbox`（JSON 指令 + ACK 回执 + 虚拟道闸模拟器）与 `mqtt-plain` 两种通道，接入真实厂商只需新增一个适配器 Bean。详见 `docs/沙箱渠道接入-支付宝与设备-2026-11-19.md`。

**支付 → 设备闭环**：核销成功后 payment 服务在**本地事务提交后**异步调用 `POST /api/device/internal/gate/release`（走 `X-Internal-Token`，非管理员 JWT），device 服务按 `device.vendor` 选适配器、优先挑在线的**出口**道闸下发 OPEN 指令，指令与设备回执可在 `GET /api/device/gate/command-log` 看到。开闸失败一律只记日志不外抛 —— 钱已经收了，不能让开闸异常影响入账；需要"先缴费后人工放行"时把 `smart-parking.gate.release-on-payment` 置 `false` 即可只入账不动闸。

**月卡记账口径**：月卡是小区主体收入，因此和停车订单走**同一张 `payment_record`、同一套收银台**，靠 `biz_type` 区分（1 停车订单 / 2 月卡；月卡 `order_id` 为 NULL、`biz_id` 存月卡订单ID）。链路：月卡下单（vehicle）→ `POST /api/payment/monthly/create`（金额以月卡订单为准，业主只能为自己的月卡付款）→ 收银台或模拟支付 → 核销后 payment 调 `POST /api/vehicle/internal/monthly/mark-paid` 幂等续期。这样 `/api/stats/income`、支付记录、对账都能看到月卡这笔钱；**发票仍只针对停车订单**（月卡发票暂不支持）。vehicle 原来的 `/monthly/pay` 自助通道已删除 —— 它会自造流水直接置成功，导致收入漏账。

**出场放行与欠费治理**：出口不硬性卡支付，而是**分级放行** —— 已支付 / 免费 / 未支付但金额 ≤ 生效信用额度时先抬杆后付费，避免出口因支付渠道故障堵死；超过额度则道闸保持关闭并返回 `needPay` + `payTip`，要求当场扫码。**生效额度 = `max(smart-parking.gate.credit-limit-amount, 该订单规则的单日封顶)`**：本系统服务小区，停车时长以天计，额度必须跟单日封顶走，否则业主回家停一晚就会被拦在出口；配置值只在规则无日封顶（月租）时兜底。语义即「一天内的停车先出场后付，跨日累积超过一天封顶才要求当场结清」，既能拦住多日占用/疑似弃车，规则调价也自动跟随。结算在放行判定之前完成，金额被锁定，付款后由支付成功事件抬杆，不会重复计费。小额欠费由 `ArrearageScanTask` 兜底：出场满 24h 发一次催缴、满 48h 记欠费，欠费次数 > 3 笔或累计 > 100 元即把车辆置黑名单（`vehicle.status=1`），下次**入场**由车牌识别直接拦停。四个参数都可用 `SP_*` 环境变量覆盖。

### 1. 激活 GitHub CI 门禁（一次性）

1. **开启 Actions 写权限**：仓库 `Settings → Actions → General → Workflow permissions` 选 **Read and write permissions** 并保存。
2. **（可选）启用 AI 分层审查**：`Settings → Secrets and variables → Actions` 中
   - Secrets 标签：新增 `ANTHROPIC_API_KEY`（启用 Claude 代码审查用）；
   - Variables 标签：新增 `ENABLE_AI_REVIEW=true`（不设置则只跑 build+test，不跑 AI 审查）。
3. **main 分支保护**：`Settings → Rules → Rulesets → New branch ruleset`
   - Ruleset Name：`main-protection`；Enforcement status：**Active**；
   - Target branches：`Add target → Include by pattern → main`；
   - Rules：勾 **Require a pull request before merging** / **Block force pushes** / **Restrict deletions**；
   - 勾 **Require status checks to pass**，搜索并选中 **`Build & Test Gate`**（工作流首次跑过后才会出现）。
4. 之后任意 push / PR 会自动触发 `AI-Native SDLC Review` 工作流；未通过 `Build & Test Gate` 的提交无法合并进 `main`。

### 2. 维护阶段监控闭环接入（Phase D）

根目录 `bands.yaml` 定义了核心指标的可接受区间（网关 p99、订单延迟/错误率、支付对账差额、MQ 积压、设备离线率等）。`smart-parking-common-core` 的 `monitor` 包提供闭环组件：

| 类 | 职责 |
| --- | --- |
| `Band` | 单条阈值配置 POJO（对应 `bands.yaml` 一条规则） |
| `BandsLoader` | 用 SnakeYAML 解析 `bands.yaml` → `List<Band>` |
| `BandEvaluator` | `evaluate()` 区间判定；`generateIntent()` 越界时生成 `intent-<date>-<metric>.md` 回流规划阶段 |
| `MetricSource` | 指标读取入口接口（实现方对接 Prometheus/Micrometer/日志） |
| `MonitorLoop` | 驱动一轮闭环：逐条读数→判定→越界则写 intent 文件 |
| `DemoMetricSource` | 演示实现，恒定返回 0.0（不触发越界），用于本地跑通流程 |

**已在 `smart-parking-stats` 模块落地真实实现**（`monitor/MonitorAutoConfiguration` 周期驱动 + `common-core` 的 `PrometheusMetricSource` 对接 Prometheus）。该模块已暴露 `/actuator/prometheus` 并被 Prometheus 抓取，运行期加载 `bands.yaml`、按 `monitor.fixed-delay-ms`（默认 60s）轮询评估，越界且 `write_intent=true` 的指标自动把 `intent-<date>-<metric>.md` 写入 `monitor.output-dir`。以下为最小接入范式，供其它模块复用：

```java
@Configuration
@EnableScheduling
public class MonitorAutoConfiguration {

    @Value("${monitor.bands-path:classpath:bands.yaml}")
    private Resource bandsResource;

    @Value("${monitor.intent-dir:./docs/sdlc}")
    private String intentDir;

    @Scheduled(fixedDelay = 60_000)
    public void tick() throws Exception {
        try (InputStream in = bandsResource.getInputStream()) {
            List<Band> bands = BandsLoader.load(in);
            // 真实环境：读取 Prometheus 的 Gauge/Micrometer MeterRegistry
            MetricSource source = band -> /* 返回 band.getMetric() 对应的当前读数 */ 0.0;
            int n = new MonitorLoop(bands, source, Path.of(intentDir)).evaluateAll();
            if (n > 0) log.warn("监控越界，已生成 {} 个 intent 需求文件", n);
        }
    }
}
```

生成的 `intent-*.md` 即下一轮「规划阶段」的需求输入，完成 **维护 → 规划** 的自动回流。

> 详见 `docs/AI原生SDLC审计与落地报告.md` 与 `docs/sdlc/plan.md`（Phase A~E）。

---

## 可观测性：监控栈一键启动（Prometheus + Grafana）

本项目已补齐监控闭环的“读数→评估→回流”全链路，并已完成**业务埋点**：

- **指标采集**：`smart-parking-stats` 及各业务服务（`gateway / order / payment / parking / device`）均引入 `micrometer-registry-prometheus` + `spring-boot-starter-actuator`，暴露 `/actuator/prometheus`，所有指标统一带 `application` 标签。
- **自动指标**：HTTP 延迟与错误率由 Actuator 自动暴露的 `http_server_requests_seconds`（Timer）提供，经 `histogram_quantile` 计算 p99 / 5xx 错误率。
- **业务语义指标（手写埋点）**：
  - `payment_recon_diff_amount`（payment，`PaymentReconMetrics`，对账差额，应恒为 0）
  - `space_cache_miss_rate_pct`（parking，`ParkingCacheMetrics`，缓存未命中率）
  - `device_offline_rate_pct`（device，`DeviceOnlineMetrics`，设备离线率）
  - `mq_order_backlog` / `mysql_active_connections`（order，`OrderBusinessMetrics`，MQ 积压 / Druid 活跃连接）
  - 各埋点组件以 `@Configuration @EnableScheduling` 自采样（JdbcTemplate / RedisTemplate / RabbitTemplate / DruidDataSource），并预留 `setXxx()` 供业务主动上报。
- **真实指标源**：`common-core` 的 `PrometheusMetricSource` 调 Prometheus `/api/v1/query` 读取 `bands.yaml` 中各指标当前值（无数据/异常返回 `NaN`，不误报）。
- **闭环驱动**：`MonitorAutoConfiguration` 周期性执行 `MonitorLoop.evaluateAll()`，越界自动生成 `intent-<date>-<metric>.md` 回流规划阶段。

### 1. 启动监控栈

```bash
# 启动 Prometheus(9090) + Grafana(3000)，并抓取全部微服务的 actuator 端点
docker-compose up -d prometheus grafana
```

> 抓取目标在 `prometheus/prometheus.yml` 中配置，已为 `gateway(8080)/order(8084)/payment(8085)/parking(8082)/device(8087)/stats(8088)` 各加一个 job（默认按 `host.docker.internal:<port>` 抓取宿主机上运行的服务）。若把这些服务也编排进 compose 网络，可改为服务名。

### 2. 查看面板

- Prometheus 查询：`http://localhost:9090`，可输入 `payment_recon_diff_amount`、`device_offline_rate_pct` 等业务指标，或 `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="smart-parking-gateway"}[5m])) by (le)) * 1000` 查网关 p99。
- Grafana：`http://localhost:3000`，账号/密码 `admin/admin`；已预置 Prometheus 数据源与「SmartParking 监控总览」面板（数据源配置见 `grafana/provisioning/`）。

### 3. 指标与阈值对照

`bands.yaml` 的 `metric` 字段已是合法 PromQL（由 `PrometheusMetricSource` 直接提交查询），与各服务暴露的指标一一对应：

| 指标（bands.yaml） | 来源 | 类型 |
| --- | --- | --- |
| `gateway_p99_latency_ms` | `http_server_requests_seconds` histogram | 自动 |
| `gateway_error_rate_pct` | `http_server_requests_seconds_count` 5xx 占比 | 自动 |
| `order_entry_p99_ms` / `order_create_error_rate_pct` | order 服务 HTTP 指标 | 自动 |
| `payment_callback_p99_ms` | payment 服务 HTTP 指标 | 自动 |
| `payment_recon_diff_amount` | `PaymentReconMetrics` Gauge | 手写 |
| `space_cache_miss_rate_pct` | `ParkingCacheMetrics` Gauge | 手写 |
| `mq_order_backlog` / `mysql_active_connections` | `OrderBusinessMetrics` Gauge | 手写 |
| `device_offline_rate_pct` | `DeviceOnlineMetrics` Gauge | 手写 |

> 手写 Gauge 的采样 SQL 依赖 `payment_reconciliation` / `parking_space` / `device` 等表结构与字段命名；若与你的实际 schema 不符，采样会安全失败（保持上一次值，不误报），按需调整 `monitor/` 包下的 SQL 即可。

