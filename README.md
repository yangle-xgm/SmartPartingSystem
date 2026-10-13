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

在 Navicat 中执行以下脚本：

1. 先执行 `mapper/init.sql` - 创建库表结构
2. 再执行 `mapper/data.sql` - 导入测试数据

### 3. 启动 Nacos 配置

访问 <http://localhost:8848/nacos，默认账号密码：nacos/nacos>

### 4. 编译项目

```bash
mvn clean install -DskipTests
```

### 5. 启动服务（按顺序）

1. `smart-parking-gateway` - 网关服务 (8080)
2. `smart-parking-user` - 用户服务 (8081)
3. 其他微服务...

### 6. 启动前端

```bash
cd smart-parking-web
npm install
npm run dev
```

访问 <http://localhost:3000>

默认账号：admin / admin123

### 7. 访问 API 文档

* Knife4j：<http://localhost:8081/doc.html>

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

## 数据库说明

所有微服务共用一个数据库 `smart_parking`，实际共 **16 张表**（计划外增量表：`community` / `reservation` / `share_income` / `feedback` / `payment_reconciliation`）。主要表如下：

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
| smart-parking-web (前端) | 3000 |

---

## CI 门禁与 AI 原生 SDLC 规范

本项目已落地 **AI 原生 SDLC（工件驱动闭环）** 的完整工程化骨架：规划(`docs/sdlc/intent.md`) → 设计(`spec.md`) → 构建(`AGENTS.md` + `plan.md`) → 测试(11 个单测 + 钩子) → 部署(`.github/workflows/ai-review.yml`) → 维护(`bands.yaml` 监控闭环)。

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

**在任意服务模块接入（示例，用 `@Scheduled` 周期执行）：**

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

