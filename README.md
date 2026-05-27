# o2o-backend：基于微服务架构的智能 O2O 聚合电商平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-blue.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-green.svg)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)

本项目是一个基于 **Spring Cloud / Alibaba 微服务生态** 研发的高性能 O2O（Online to Offline）同城聚合电商后台。系统采用**“独占数据库 (Database-per-Service)”**的物理隔离架构，专注于解决同城高并发搜索、高频详情页访问、以及秒杀期间瞬时流量洪峰等典型生产痛点。

---

## 🏗️ 仓库目录结构说明

为了保持仓库的高整洁度与高可读性，项目采用如下结构组织：

```
o2o-project/ (项目根目录)
 ├── docs/                          # 📖 项目文档目录
 │    ├── o2o-backend_微服务与业务规划书_基础版.md  # 业务设计及技术痛点说明
 │    ├── o2o-backend_数据库表设计.md             # 微服务隔离数据库表结构及 DDL
 │    └── o2o-backend_API接口设计.md              # 前后端交互 API 规范定义
 ├── deploy/                        # ⚙️ 本地与生产一键部署目录
 │    ├── docker-compose.yml        # MySQL、Redis、RabbitMQ、Nacos 一键编排文件
 │    ├── nginx.conf                # Nginx 反向代理配置
 │    ├── dist/                     # 前端打包好的静态资源
 │    └── docker-data/              # [GitIgnore] 本地中间件挂载的数据文件
 ├── o2o-backend/                   # 💻 后端 Java 微服务工程源码
 │    ├── pom.xml                   # 父工程 pom (依赖版本锁定)
 │    ├── o2o-common/               # 公共工具包 (统一返回 Result、全局异常捕获)
 │    ├── o2o-api/                  # RPC 远程接口声明及共享 DTO
 │    ├── o2o-gateway/              # 系统统一网关服务 [8080]
 │    ├── o2o-user-service/         # 用户与购物车微服务 [8081]
 │    ├── o2o-shop-service/         # 店铺与商品搜索微服务 [8082]
 │    └── o2o-trade-service/        # 交易与秒杀下单微服务 [8083]
 └── .gitignore                     # Git 忽略配置
```

---

## 🛠️ 技术选型

| 技术维度 | 选型组件 | 作用说明 |
| :--- | :--- | :--- |
| **核心框架** | Spring Boot 3.2.5 / Java 17 | 现代企业级开发基石与新特性支持 |
| **微服务治理** | Spring Cloud 2023.0.1 / Nacos 2.2.3 | 服务的注册发现、负载均衡与配置中心管理 |
| **网关路由** | Spring Cloud Gateway | 统一请求入口、JWT 验签与全局流控 |
| **数据持久化** | MySQL 8.0 / MyBatis-Plus 3.5.6 | 核心业务数据持久化，基于 MPP 提高开发效率 |
| **缓存与检引** | Redis 7.0 / Redisson | 店铺详情高并发逻辑过期缓存、LBS 地理检索、分布式锁 |
| **消息中间件** | RabbitMQ 3-management | 秒杀高并发下单的异步削峰与落库队列 |

---

## 🚀 快速启动指南

### 1. 启动本地开发环境 (Docker)
确保你的电脑上安装并启动了 Docker，然后在终端中执行以下命令：
```bash
cd deploy
docker compose up -d
```
*启动成功后，你可以通过 `http://localhost:8848/nacos` 访问 Nacos 控制台（默认密码: nacos/nacos）。*

### 2. 导入数据库表结构
1. 使用客户端工具（如 Navicat）连接本地 MySQL：`127.0.0.1:3306`（用户名：`root`，密码：`root`）。
2. 在查询窗口中运行 [o2o-backend_数据库表设计.md](docs/o2o-backend_%E6%95%B0%E6%8D%AE%E5%BA%93%E8%A1%A8%E8%AE%BE%E8%AE%A1.md) 中的建表语句，自动创建 `o2o_user_db`、`o2o_shop_db`、`o2o_trade_db` 库和表。

### 3. 运行 Java 微服务
1. 使用 **IntelliJ IDEA** 打开 `o2o-backend` 目录，让 Maven 自动同步依赖。
2. 依次启动以下服务：
   * `GatewayApplication` (网关)
   * `UserApplication` (用户服务)
   * `ShopApplication` (店铺服务)
   * `TradeApplication` (交易服务)

---

## 💬 核心业务流程与设计文档
详细的业务流转（如 LBS 地理检索、JWT 网关透传、秒杀 Lua 预扣库存与 MQ 削峰）请查阅：
* 📄 [微服务业务规划设计书](docs/o2o-backend_%E5%BE%AE%E6%9C%8D%E5%8A%A1%E4%B8%8E%E4%B8%9A%E5%8A%A1%E8%A7%84%E5%88%92%E4%B9%A6_%E5%9F%BA%E7%A1%80%E7%89%88.md)
* 📄 [API 接口定义规范](docs/o2o-backend_API%E6%8E%A5%E5%8F%A3%E8%AE%BE%E8%AE%A1.md)
