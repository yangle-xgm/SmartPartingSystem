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

所有微服务共用一个数据库 `smart_parking`，包含以下表：

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

