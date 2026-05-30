# O2O 智能聚合电商与大模型网关 - AI Agent 全局上下文规范

> **💡 说明**：本文件是专为 AI 编码助手（如 Cline, Cursor, Antigravity 等）准备的全局上下文指导说明。AI 在阅读、设计和修改本项目代码时，**必须**严格遵循本规范。

---

> **
最重要的：只有我发送“开始写代码”，“开始写”，“完成代码书写”这样的字段时，才能去修改我的文件项目，不然一律按照给我在聊天界面或计划书的形式呈现

## 🏗️ 1. 项目架构与模块分工

项目采用 Maven 多模块（Multi-Module）架构，所有服务通过 Nacos 实现服务发现与配置管理。

### 1.1 模块映射及运行端口
| 模块名称 | 端口 | 核心职责 | 数据库隔离 |
| :--- | :--- | :--- | :--- |
| `o2o-gateway` | `8080` | 系统统一网关、JWT 验签、大模型 Token 限流、模型故障熔断降级 | 无数据库 |
| `o2o-user-service` | `8081` | 用户资料、地址簿管理、基于 Redis Hash 购物车暂存 | `o2o_user_db` |
| `o2o-shop-service` | `8082` | 店铺商品管理、附近检索(Geo)、AI 评价总结、Qdrant 向量检索 | `o2o_shop_db` |
| `o2o-trade-service` | `8083` | 普通下单与秒杀下单、Redis Lua 预扣库存、MQ 异步削峰、AI 黄牛阻断 | `o2o_trade_db` |
| `o2o-agent-service` | `8084` | AI 会话历史管理、Function Calling 工具函数绑定与微服务 RPC 代理 | 无数据库 |
| `o2o-common` | N/A | 公共依赖包（被所有服务依赖）：Result、全局异常、密码加密等工具 | 无数据库 |
| `o2o-api` | N/A | 跨服务 Feign 接口定义与公共 DTO 声明（防止循环依赖） | 无数据库 |

---

## 🛠️ 2. 技术栈约束与中间件连接

在编写任何新功能或修改代码时，不得随意引入其他技术选型，必须使用现有中间件体系：
* **核心框架**：Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Java 17 / Maven 3.x
* **数据访问**：MySQL 8.0 / MyBatis-Plus 3.5.6
* **缓存与锁**：Redis 7.0 / Redisson 
* **消息中间件**：RabbitMQ 
* **向量数据库**：Qdrant (端口 6333-HTTP / 6334-gRPC)
* **服务网关**：Spring Cloud Gateway (基于 Netty/WebFlux，不可混入 Web/Tomcat 依赖)

---

## 🔐 3. 核心机制与开发规范 (AI 必读)

### 3.1 身份安全与上下文传递 (`X-User-Id`)
1. **禁止越权直接获取 `userId`**：Controller 层的 C 端接口，**严禁**从前端传递的 RequestBody 或 Query 中直接获取 `userId`。
2. **唯一可信来源**：用户身份由网关 `o2o-gateway` 统一验签，并通过请求头 `X-User-Id` 隐式传递。
3. **本地上下文提取**：各微服务内注册了 `UserContextInterceptor`，拦截器会自动将 `X-User-Id` 写入 ThreadLocal 的 `UserContext`。
   * 获取当前用户 ID 方法：`Long userId = UserContext.getUser();`
4. **RPC 传递**：通过 Feign 调用下游服务时，必须走 `FeignHeaderInterceptor`，无感将上下文中的 `X-User-Id` 封入 Feign 请求头。

### 3.2 统一返回结果与异常处理
1. **控制器返回值**：所有 Controller 接口必须统一返回 `com.o2o.common.Result<T>`。例如：`return Result.ok(data);` 或 `return Result.fail("错误提示");`。
2. **异常抛出规范**：业务层遇到错误，**严禁**直接返回错误字符串，应抛出统一业务异常：
   ```java
   throw new BusinessException("异常提示消息");


## 🔐 4. 严禁与限制行为
### 4.1 禁止修改父工程配置：未经用户允许，严禁擅自修改根目录下 o2o-backend/pom.xml 中的依赖版本锁
### 4.2 禁止直接引用实现类：微服务间的 RPC 调用，必须将 FeignClient 接口定义在 o2o-api 模块，并通过依赖 o2o-api 引入，绝对不允许直接将子服务 o2o-xxx-service 引入另一个子服务作为 Maven 依赖。
### 4.3 禁止残留测试代码：切勿保留 System.out.println 等测试输出，统一使用 Lombok 的 @Slf4j 打印日志。
### 4.4 禁止污染 ThreadLocal：使用 ThreadLocal 必须在 finally 块中调用 remove() 以免内存泄漏。
### 4.5 数据库变更说明：若涉及到表结构变更，严禁直接用客户端修改，必须将 DDL 语句同步追加更新至 docs/o2o-backend_数据库表设计.md 中