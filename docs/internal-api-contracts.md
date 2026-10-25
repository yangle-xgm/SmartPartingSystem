# 跨服务 /internal 接口契约清单

> 本文件是服务间内部接口的**唯一权威契约**（single source of truth）。
> 跨服务取数铁律（AGENTS.md §3）：服务只能通过自己的 Mapper 访问自己 owns 的表；跨服务取数必须调
> `/internal/**` 接口，网关不对外暴露 `/internal/**`（`AuthGlobalFilter` 白名单仅放行 `/api/**/internal/**`）。
>
> 实现形态：`@Component` + `RestTemplate` + `@Value` 配置 URL（非 OpenFeign）。
> 每个 provider 模块都有对应的 `*InternalApiContractTest`（`@WebMvcTest` + MockMvc）守护本清单，
> **修改任何一侧（消费者传参 / 提供方签名 / Result 结构）必须先改本文件并同步测试**。

## 1. 契约总表

| # | 消费者（调用方） | 提供方 | 方法与路径 | 关键契约点 | 契约测试 |
|---|----------------|--------|-----------|-----------|---------|
| 1 | order/payment/vehicle 的 `AuditClient.record()` | user | `POST /api/audit/internal/record` | query：`operation`(必填), `method`, `isSuccess`(0/1), `userId`?, `username`?, `params`?, `ip`?, `costTime`?；返回 `Result<Void>` | `UserInternalApiContractTest` |
| 2 | notify `NotificationController.fetchOwnerIds()`（广播拉业主） | user | `GET /api/user/internal/owner-ids` | `$.data` 必须是**裸 Long 数组**（消费者 `Long.valueOf(String.valueOf(o))`） | `UserInternalApiContractTest` |
| 3 | order `ParkingSpaceClient.updateStatus()` | parking | `PUT /api/parking/internal/space/{id}/status?status=` | 0 空闲 / 1 占用；`Result<Void>` | `ParkingInternalApiContractTest` |
| 4 | order `ParkingSpaceClient.claimSpace()`（入场防并发） | parking | `POST /api/parking/internal/space/{id}/claim` | `$.data` 必须是**裸 Boolean**（`Boolean.TRUE.equals` 解析） | `ParkingInternalApiContractTest` |
| 5 | order `ParkingSpaceClient.getSpaceInfo()`、payment `handleShareIncome()`（分润链路） | parking | `GET /api/parking/internal/space-info/{id}` | `$.data` 必须含 `id`/`spaceNo`/`ownerId`/`isShared` 四字段；`isShared` 必须是 Boolean（未共享=false，非 null）；不存在时 `code!=200` + `data` 缺省 | `ParkingInternalApiContractTest` |
| 6 | order `VehicleClient.getVehicleByPlate()`、notify `OrderMessageConsumer` | vehicle | `GET /api/vehicle/internal/info/{plateNo}` | 路径变量为 **URL-encode 后的车牌**；`$.data` 为 Vehicle；未注册 `code!=200` + message=车辆未注册 | `VehicleInternalApiContractTest` |
| 7 | order `VehicleClient.listVehicleIdsByOwner()`（订单数据隔离） | vehicle | `GET /api/vehicle/internal/list-by-owner?ownerId=` | `$.data` 必须是**裸 Long 数组**（逗号拼接 vehicleIds） | `VehicleInternalApiContractTest` |
| 8 | —（黑名单/运维链路预留） | vehicle | `PUT /api/vehicle/internal/{id}/status?status=` | `Result<Void>` | `VehicleInternalApiContractTest` |
| 9 | payment `paymentSuccess()`（支付成功直连兜底） | order | `POST /api/order/internal/confirm-payment?orderId=&amount=` | `amount` 按 `BigDecimal.toString()` 拼接，provider 必须按 BigDecimal 绑定 | `OrderInternalApiContractTest` |
| 10 | payment `PaymentTimeoutTask.processTimeout()` | order | `POST /api/order/internal/payment-timeout?orderId=` | `Result<Void>` | `OrderInternalApiContractTest` |
| 11 | payment 退款回调 | order | `POST /api/order/internal/refund-callback?orderId=` | `Result<Void>` | `OrderInternalApiContractTest` |
| 12 | 业主端人工确认车牌（suggestList 选择） | order | `POST /api/order/internal/confirm-plate?plateNo=&deviceNo=HUMAN` | `deviceNo` 缺省 HUMAN；走 deviceEntry 同一链路 | `OrderInternalApiContractTest` |
| 13 | payment `handleShareIncome()`（拿 spaceId） | order | `GET /api/order/internal/order-info?orderId=` | `$.data.spaceId` 必须是 Number（`((Number) map.get("spaceId")).longValue()`） | `OrderInternalApiContractTest` |
| 14 | device `DeviceServiceImpl.handlePlateRecognition()`（MQTT 摄像头联动入场） | order | `POST /api/order/internal/device-entry?plateNo=&confidence=&deviceNo=` | 车牌需 URL-encode；`$.data` 必须含 `recognized`/`orderNo`/`spaceNo`/`message` 四字段（控制道闸） | `OrderInternalApiContractTest` |
| 15 | device `mockExitRecognize()`（出口摄像头联动出场） | order | `POST /api/order/internal/device-exit?plateNo=&deviceNo=` | `$.data` 为结算结果 Map | `OrderInternalApiContractTest` |
| 16 | stats `ParkingStatsServiceImpl.queryOngoingCount()` | order | `GET /api/order/internal/ongoing-count` | `$.data` 必须是**裸 Number**（`((Number) data).longValue()`）；失败返回 -1 | `OrderInternalApiContractTest` |
| 17 | 运维/回调链路 | payment | `POST /api/payment/internal/success?payNo=&transactionId=` | `Result<Void>` | `PaymentInternalApiContractTest` |
| 18 | 运维/回调链路 | payment | `POST /api/payment/internal/refund?payNo=` | `Result<Void>` | `PaymentInternalApiContractTest` |
| 19 | 管理端演示/运维 | payment | `POST /api/payment/internal/timeout-scan` | `$.data.message` 固定文案"支付超时扫描已执行" | `PaymentInternalApiContractTest` |
| 20 | stats `queryPaidIncome()`（收入权威口径） | payment | `GET /api/payment/internal/income-summary?startDate=&endDate=` | 日期 `yyyy-MM-dd`；`$.data.total` 必须是 Number（`new BigDecimal(total.toString())`） | `PaymentInternalApiContractTest` |
| 21 | MQTT 设备心跳上报 | device | `POST /api/device/internal/heartbeat?deviceNo=` | `Result<Void>` | `DeviceInternalApiContractTest` |
| 22 | MQTT 设备状态变更 | device | `PUT /api/device/internal/{deviceNo}/status?status=` | 路径变量 + query；`Result<Void>` | `DeviceInternalApiContractTest` |
| 23 | 各服务回写统计（MQ/业务链路） | stats | `POST /api/stats/internal/update?type=` | `type` 走 query，业务数据走 JSON body（绑定为 `Map<String,Object>`） | `StatsInternalApiContractTest` |
| 24 | 订单/设备链路（入场推送） | notify | `POST /api/notify/internal/entry?userId=&plateNo=&spaceNo=` | type=1；content 含车牌与车位号 | `NotifyInternalApiContractTest` |
| 25 | 订单/设备链路（出场推送） | notify | `POST /api/notify/internal/exit?userId=&plateNo=&duration=&amount=` | type=2；content 含时长与费用 | `NotifyInternalApiContractTest` |
| 26 | 订单/支付链路（待支付提醒） | notify | `POST /api/notify/internal/payment?userId=&orderNo=&amount=` | type=3；content 含订单号与金额 | `NotifyInternalApiContractTest` |

## 2. 统一响应契约（Result<T>）

所有 `/internal` 接口返回 `com.smartparking.common.core.domain.Result<T>`：

```json
{ "code": 200, "message": "操作成功", "data": <T|null>, "timestamp": 1699000000000 }
```

- 成功：`code=200`，业务载荷在 `data` 层；`Result<Void>` 接口 `data` 缺省（null）。
- 业务失败：`Result.fail(message)` → `code=500` + `data` 缺省；`BizException` → `code=<ErrorCode>`。
- 参数缺失/类型错误：由各模块 `GlobalExceptionHandler` 的 `@ExceptionHandler(Exception.class)`
  捕获，同样返回 HTTP 200 + `code=500`（**不是 400**）。消费者统一按 `code!=200` 或 `data==null` 走降级路径。
- 消费者解析范式：`resp.get("data")`（Jackson 反序列化为 Map 后取 data 层）；
  裸值（Boolean/Number/数组）直接用 `instanceof` 判断，**提供方不得给这些字段套额外包装**。

## 3. 契约测试说明

- 位置：各 provider 模块 `src/test/java/com/smartparking/<模块>/<模块>InternalApiContractTest.java`。
- 形态：`@WebMvcTest(controllers = ...)` + MockMvc，service 层 `@MockBean`；
  按**消费者真实传参**（含 URL-encode 车牌、BigDecimal 字符串、最小参数集）发请求，
  断言 Result 契约 + service 入参透传。
- 运行：`mvn -B -pl smart-parking-user,smart-parking-parking,... -am test -DfailIfNoTests=false`
  （与 CI 的 `mvn test` 等价，无 DB/Nacos 依赖）。
- **MockMvc 编解码注意**：standalone MockMvc 不像 Tomcat 那样对路径中的 `%XX` 做 URL-decode。
  消费者 `URLEncoder.encode(车牌)` + 容器 decode 的净效果 = controller 收到原始中文车牌，
  故测试中中文路径变量直接以原始中文请求（编解码由 Servlet 容器负责，不在 MockMvc 层模拟）。
- 新增/修改 `/internal` 接口时：先更新本清单 → 再改实现 → 最后补/改契约测试，三者保持一致。

## 4. 已知取舍

- 当前为 provider 侧 MockMvc 契约（轻量、无外部依赖），未做 consumer-driven 的 Pact 式双向契约；
  如后续引入 OpenFeign，可将本清单的表格直接转为 Pact 交互。
- 跨服务调用的超时/重试策略内置于各 Client（连接 2-3s / 读取 2-5s，失败仅告警不阻断主流程），
  属实现细节不在契约层。
