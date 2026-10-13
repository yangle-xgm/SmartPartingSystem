# plan.md — 实施计划（构建阶段产出）

> 工件来源：AI 原生 SDLC「构建阶段」产出，承接 `spec.md`。所有改动基于本计划，禁止临时随意开发。
> Source: 关联 `docs/sdlc/spec.md`

## 总体目标
用 AI 原生 SDLC 补齐项目缺失的「规范 / 测试 / CI / 监控闭环」四类工件，并定位修复构建中断，
使 `mvn clean install` 与 `mvn test` 全量通过，并形成「监控越界 → 自动生成 intent.md」的维护闭环。

---

## Phase A — 修复构建门禁（最高优先，P0）

**Files to change, in order**
1. 本地执行 `mvn -B clean install -DskipTests` 复现，定位 `smart-parking-user` 失败根因（编译错/缺依赖/资源缺失）。
2. 修复后提交；若根因是某依赖未声明，在对应模块 `pom.xml` 补 `<dependency>`；若是公共类缺失，在 `common-core` 补齐或调整 `<modules>` 顺序。
3. 新增 `.github/workflows/ai-review.yml` 的 `build` 任务作为永久门禁。

**Tests that must pass**
- `mvn -B clean install -DskipTests` 全模块成功。
- 后续 `mvn -B test -DfailIfNoTests=false` 不出现编译/打包失败。

**Rollback**：仅改 POM/配置时，`git revert` 单文件即可；若动了业务代码，先 `git stash` 保留现场再定位。

---

## Phase B — 补齐单元测试（P1） ✅ 已完成

**Files to change, in order**
1. `scripts/run-tests.sh`：统一测试入口（CI 与本地钩子共用）。
2. 各模块补纯逻辑测试：
   - `smart-parking-parking`：车位状态机（空闲/占用/预约/维护）转换。
   - `smart-parking-vehicle`：模糊车牌识别兜底逻辑。
   - `smart-parking-payment`：对账差额计算、超时补偿判定。
   - `smart-parking-order`：访客邀请码生成/校验（扩展现有 `BillingAndErrorCodeTest` 范式）。
   - `smart-parking-gateway`：JWT 校验、白名单路由单测。
3. 接入 `.claude/settings.json` 的 PostToolUse 钩子，编辑后自动跑 `scripts/run-tests.sh`。

**Tests that must pass**：新增测试全部绿；既有 `BillingAndErrorCodeTest` 不被破坏。

**Rollback**：测试为新增文件，删除即可回滚，无业务风险。

---

## Phase C — CI 分层审查（部署阶段，P1）

**Files to change, in order**
1. `.github/workflows/ai-review.yml`：
   - `build` job：JDK 17 → `mvn clean compile` → `mvn test -DfailIfNoTests=false`。
   - `review` job：接入官方 AI 审查（Claude Code Action 为主，Codex/Gemini 可选），仅标记高危项；高危变更（认证/支付/库迁移）强制人工终审。
2. 分支保护：开启 PR 必须过 `build` 门禁；`main` 禁止直接 push。

**Tests that must pass**：PR 触发 CI，build 绿；review 产出报告。

**Rollback**：删 workflow 文件或关闭分支保护。

---

## Phase D — 监控闭环（维护阶段，P2） ✅ 已完成

**Files to change, in order**
1. `bands.yaml`：定义核心指标区间（p99 延迟、错误率、MQ 积压、设备离线率）。
2. 监控采集（Micrometer/Prometheus 或日志）读取 `bands.yaml`，越界触发告警。
3. 越界时自动生成 `docs/sdlc/intent-<date>-<metric>.md`（即新的规划需求），回流到 Phase A 循环。

**Tests that must pass**：可用单测验证 band 解析与“越界→生成 intent”逻辑。

**Rollback**：停止采集任务即停闭环。

**已落地（2026-10-12 → 2026-10-13）**：`common-core` 新增 `monitor/` 包——
- `Band.java`（阈值 POJO）、`BandsLoader`（SnakeYAML 解析 bands.yaml）、`BandEvaluator`（evaluate + generateIntent）；
- `MetricSource`（指标读取接口）、`MonitorLoop`（驱动一轮闭环，越界则写 intent 文件）、`DemoMetricSource`（演示实现）；
- 测试：`BandEvaluatorTest` + `MonitorLoopTest`（验证越界→生成 intent、无越界→不生成）。
- `README.md` 已补充「CI 门禁激活 + 监控闭环接入」章节与 `@Scheduled` 接入示例；DB 表清单同步为 16 张。
- 集成侧（定时采集 + 真实指标源）已给出可复制示例，落地即把 `MetricSource.sample()` 接到 Prometheus/Micrometer 即可。

---

## Phase E — 文档同步（贯穿，P1）

**Files to change, in order**
1. 把 `smart-parking-ai` 模块补登记进 `计划书.md` §二 微服务划分（端口、职责、部署）。
2. 把增量表 `community/reservation/share_income/feedback/payment_reconciliation` 补进 `计划书.md` §4.2，并导出 ER 图。
3. 本 `AGENTS.md` / `intent.md` / `spec.md` / `plan.md` / `bands.yaml` 纳入版本控制，随代码提交。

**Tests that must pass**：文档与 `init.sql` 表清单一致（人工核对）。

**Rollback**：文档改动 `git revert`。
