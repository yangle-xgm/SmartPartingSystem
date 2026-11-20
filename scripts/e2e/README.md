# E2E 自动化测试（Playwright）

覆盖此前缺失的两条端到端链路（订单出场结算→支付→退款、预约取消），
并把前端功能测试阶段验证过的关键回归点固化为可重复执行的用例。

## 1. 前置条件

| 依赖 | 说明 |
| --- | --- |
| Docker 容器 | `sp-mysql`、`sp-redis`、`sp-nacos`、`sp-rabbitmq` 已运行（`docker compose up -d`） |
| 后端服务 | 网关 8080 与 8 个业务服务（8081-8088、8090）健康 |
| 前端 dev | 管理端 `npm run dev --prefix smart-parking-web`（**3000**）、业主端 `npm run dev --prefix smart-parking-h5`（**3001**） |
| 浏览器 | 复用本机 Microsoft Edge（`channel: 'msedge'`），**无需** `npx playwright install` |

> 仅跑 API 用例（`npm run test:api`）时不需要前端 dev server。

## 2. 运行

```bash
cd scripts/e2e
npm install            # 首次

npm run test:api       # 只跑接口链路（推荐，快且稳定）
npm run test:pc        # 管理端 UI 冒烟
npm run test:h5        # 业主端 H5 冒烟
npm test               # 全量
npm run report         # 查看 HTML 报告
```

## 3. 用例清单

| 文件 | 覆盖内容 |
| --- | --- |
| `tests/api/order-payment-refund.api.spec.js` | 入场 → 出场结算（金额>0）→ 创建支付流水（金额以订单为准）→ 支付成功 → 订单状态同步 → 退款 → 退款后状态回退；已支付订单不可重复发起支付 |
| `tests/api/reserve-cancel.api.spec.js` | 预约 → 我的预约可查 → 取消 → 不再进行中；重复取消被拒；时间边界（结束早于开始 / 超时长 / 过去时间）统一返回 5206 |
| `tests/api/soft-delete-recreate.api.spec.js` | 软删除唯一键回归（D4）：在用编号重复创建返回 409，删除后可反复用同一编号重建 |
| `tests/ui/pc-pages.pc.spec.js` | 管理端登录跳转、车位表格渲染、状态筛选结果自洽且无 console 报错 |
| `tests/ui/h5-smoke.h5.spec.js` | 业主端未登录跳转、登录后首页无 console 错误、无横向溢出 |

## 4. 测试数据隔离

- 每个用例通过 `POST /api/parking/space` 自建 `E2E-*` 前缀车位，结束后按 `space_no` 物理清理车位、订单、支付流水、预约。
- 金额场景用 SQL 夹具把 `parking_order.entry_time` 回拨 3 小时（真实等待不可行），属测试预处理，不修改业务代码。
- 登录复用真实验证码：从页面/接口拿到 `captchaKey` 后读 `sp-redis` 的 `captcha:<key>`，不绕过校验。

## 5. 环境变量

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `API_BASE_URL` | `http://localhost:8080` | 网关地址 |
| `PC_BASE_URL` | `http://localhost:3000` | 管理端地址 |
| `H5_BASE_URL` | `http://localhost:3001` | 业主端地址 |
| `E2E_ADMIN_USER` / `E2E_ADMIN_PASSWORD` | `admin` / `admin123` | 管理端账号 |
| `MYSQL_CONTAINER` / `MYSQL_PASSWORD` | `sp-mysql` / `21491879` | SQL 夹具与数据清理 |
| `REDIS_CONTAINER` | `sp-redis` | 读取验证码 |
| `SP_INTERNAL_TOKEN` | `SmartParking-Internal-2026` | 访问 `/api/**/internal/**` 的内部令牌 |

## 6. 已知约束

- 用例依赖本机 Docker（`docker exec` 读取验证码、执行 SQL 夹具），不适合在无 Docker 的 CI agent 上直接跑；CI 化时改为直连 MySQL/Redis 的 Node 客户端即可。
- 内部接口必须携带 `X-Internal-Token`，缺失会被网关 403 拦截 —— 这也是 `payment.create` 曾恒返回 5402 的根因，相关修复见 `PaymentRecordServiceImpl`/`NotificationController` 的 RestTemplate 拦截器。
