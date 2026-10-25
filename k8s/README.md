# 智慧停车系统 K8s 部署清单

单体仓库内 12 个微服务的 Kubernetes 部署清单。基础设施（MySQL / Redis / Nacos / RabbitMQ）
**外部托管**（云 RDS、云 Redis、自建 Nacos 等），不随本清单编排；仅通过地址配置注入。

## 文件说明

| 文件 | 内容 |
| ---- | ---- |
| `00-namespace.yaml` | 命名空间 `smart-parking` |
| `01-configmap-infra.yaml` | 外部基础设施地址（Nacos/MySQL/Redis/RabbitMQ）——**换环境改这里** |
| `02-configmap-app.yaml` | `common.yml`：服务间调用 URL（集群 Service DNS）+ Flyway 开启 + 数据源占位符 |
| `03-secret.yaml` | MySQL 密码 Secret（演示默认值，**生产必须替换**） |
| `10..19-deployment-*.yaml` | 10 个微服务 Deployment + ClusterIP Service |
| `20-ingress.yaml` | gateway Ingress（对外入口，Nginx Ingress Controller） |

## 启动顺序（user 先启动）

- `smart-parking-user` 无 initContainer，最先就绪（数据库外键以 user 为根：parking_space/vehicle/payment_record 等均引用 sys_user）。
- 其余服务 Deployment 带 initContainer（`nc -z` 等待上游 Service TCP 就绪），apply 时自动按依赖顺序启动：
  - parking / vehicle → user
  - order / payment / notify → user + 其上游
  - device → order；stats → order + payment
  - ai / gateway 无 DB 依赖

## 部署步骤

```bash
# 1. 构建并推送镜像（镜像名：smart-parking-<模块>:latest，与 Deployment 一致）
mvn -B clean install -DskipTests
# 如有镜像仓库：docker build -t <registry>/smart-parking-user:latest smart-parking-user/ && docker push ...

# 2. 修改外部基础设施地址
vi k8s/01-configmap-infra.yaml   # NACOS_ADDR / MYSQL_HOST / REDIS_HOST / RABBITMQ_HOST ...

# 3. 替换生产数据库密码（重要）
kubectl create secret generic smart-parking-secret \
  --from-literal=MYSQL_PASSWORD=<生产密码> -n smart-parking --dry-run=client -o yaml | kubectl apply -f -

# 4. 按序 apply（initContainer 会保证运行时依赖顺序）
kubectl apply -f k8s/

# 5. 查看
kubectl -n smart-parking get pods -w
```

## 关键设计

1. **Flyway**：`SPRING_PROFILES_ACTIVE=prod` + `common.yml` 中 `spring.flyway.enabled=true`，
   每模块独立迁移（`schema_history_<模块>` 表 + `classpath:db/migration`），CI/本地仍默认关闭。
2. **服务间调用**：客户端（AuditClient/ParkingSpaceClient 等）的 `smart-parking.*-url` 缺省值是 localhost，
   K8s 里由 `common.yml` 覆盖为集群 Service DNS；`/internal/**` 仅集群内可达（Ingress 不暴露，符合网关白名单契约）。
3. **健康检查**：当前多数业务模块未接 actuator，统一用 TCP 探针；后续接入 actuator 后可升级为
   `httpGet /actuator/health`（order/payment/parking/device/stats/gateway 已具备）。
4. **资源配置**：requests 250m/512Mi、limits 1000m/1Gi，按需调整。

## 待办（超出清单范围）

- HPA 自动扩缩（基于 CPU/自定义指标）
- PDB（Pod 反亲和与中断预算）
- NetworkPolicy（限制 /internal 仅集群内互调）
