# 店铺与商品微服务 (o2o-shop-service) 核心业务与架构设计总结

本文档总结了智能 O2O 聚合电商平台中**店铺与商品微服务**的核心业务逻辑、多端解耦架构设计以及数据安全防越权方案，为后续开发与系统审计提供设计依据。

---

## 1. 业务与架构定位：领域内聚，逻辑隔离（RuoYi-Cloud 风格）

为了防止微服务“模块爆炸”并减轻早期系统编译、部署与运维的物理开销，我们采用**“单一微服务 + 包级逻辑隔离”**的架构设计，即只用一个 `o2o-shop-service` 微服务（端口 8082）处理整个店铺和商品域。

通过在包结构和接口路由层面的彻底划分，实现了 C端消费者（只读）与 B端商家（管理写）的完美隔离：

### 1.1 架构对比与选型决策

| 评估维度 | 逻辑分包隔离（最终采纳） | 物理微服务拆分（o2o-shop-portal & admin） |
| :--- | :--- | :--- |
| **工程膨胀度** | **低**。仅保留 1 个微服务，业务高度内聚。 | **高**。裂变为 3 个子模块，导致 pom 依赖与配置爆炸。 |
| **运行开销** | **小**。仅需 1 个 Spring Boot 进程，节省内存资源。 | **大**。需要启动 2 个 JVM 进程，资源开销翻倍。 |
| **开发协作** | **顺畅**。代码集中在单一工程，避免跨模块引用的循环依赖。 | **繁琐**。需在多模块间频繁切换，同步依赖版本成本高。 |
| **网关路由** | **极简**。网关只配置一条 `/api/shop/**` 规则路由，前台统一。 | **复杂**。网关需分流路由到两个不同端口的服务，配置繁多。 |
| **性能扩展性** | **高**。支持通过数据库**主从读写分离**及 Redis/ES 缓存进行性能隔离。 | **物理隔离**。进程级隔离，但存在过度设计。 |

---

## 2. 代码结构与分包隔离规范

在 `o2o-shop-service` 内部，Controller 和 Service 被严密地划分为 `customer`（客户端）与 `merchant`（商家端）包目录：

```text
o2o-shop-service
├── src/main/java/com/o2o/shop
│   ├── controller
│   │   ├── customer                # C端消费者只读接口 (RequestMapping: /shop/customer/**)
│   │   │   └── GoodsCustomerController.java (获取指定店铺上架商品)
│   │   └── merchant                # B端商家管理接口 (RequestMapping: /shop/merchant/**)
│   │       └── GoodsMerchantController.java (商家添加与上架商品)
│   │
│   ├── service
│   │   ├── customer                # C端只读业务逻辑
│   │   │   ├── GoodsCustomerService.java
│   │   │   └── impl/GoodsCustomerServiceImpl.java
│   │   └── merchant                # B端商家写业务逻辑 (防越权校验核心)
│   │       ├── GoodsMerchantService.java
│   │       └── impl/GoodsMerchantServiceImpl.java
│   │
│   ├── entity                      # 实体对象层 (Shop.java, Goods.java, Review.java)
│   └── mapper                      # 统一数据访问层 (ShopMapper.java, GoodsMapper.java, ReviewMapper.java)
```

---

## 3. `owner_id` 的防越权获取方式选型

为防止“商家A横向越权操作商家B的店铺”，接口必须校验操作者与商铺的所有权。我们对如何获取并校验 `owner_id`（商铺主人）进行了深度论证：

### 3.1 方案对比与选型依据

| 评估维度 | 方式一：实时数据库/Redis 缓存查询 (最终采纳) | 方式二：在 JWT Token 载荷 (Payload) 中直接塞入 `shopId` |
| :--- | :--- | :--- |
| **实时生效性** | **高**。店铺绑定关系、启用状态一旦在库中更新，**下一秒请求立刻生效**，安全防护无死角。 | **低**。JWT 签发后不可变。绑定变更时，必须等 Token 过期或强迫商家退登重签，存在安全空窗期。 |
| **系统域耦合** | **低**。鉴权服务（`o2o-user-service`）只认人，不干涉店铺业务，微服务限界上下文边界清晰。 | **高**。用户服务签发 Token 时必须跨域查询店铺关系，造成服务间强耦合。 |
| **多店铺支持** | **好**。一个商家账号绑定多家店铺（如连锁分店）时，通过关系表可轻松适配。 | **差**。若商家绑定十多个店铺，写入 JWT 会使 Token 头部急剧膨胀甚至撑爆 Header。 |
| **数据库压力** | **极低开销**。B端修改商品的频率极低。且可通过 Redis 缓存（缓存 key: `o2o:shop:owner:{userId}`）进行提速。 | **无开销**。网关解析 Token 即可拿到，不需要任何查询。 |

### 3.2 最终防越权校验逻辑实现
我们选择**“实时查库（带缓存）”**的安全模型：
1. **网关透传**：网关验证 Token 合法后，提取 `userId`，并在请求头中隐式注入 `X-User-Id`。
2. **拦截器装配**：`o2o-shop-service` 注册 `UserContextInterceptor`，自动提取该头并写入 `ThreadLocal` 的 `UserContext` 中，请求结束时自动清理，防止线程复用污染。
3. **业务层强校验**：
   * **未传 shopId**：后端自动执行 `SELECT id FROM tb_shop WHERE owner_id = currentUserId`，将查出的 `shopId` 强制赋值给商品，防止商家随意挂靠。
   * **传了 shopId**：后端获取店铺元数据后校验 `shop.getOwnerId().equals(currentUserId)`。一旦不匹配，立刻抛出 `403 Forbidden` 异常。

---

## 4. 网关路由与接口映射

`o2o-gateway` 采用扁平化的单路路由配置，由微服务内部路由前缀进行细化拦截：

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 店铺微服务路由
        - id: shop-service-route
          uri: lb://o2o-shop-service
          predicates:
            - Path=/api/shop/**
          filters:
            - StripPrefix=1
```

* **C端请求路由路径**：`GET /api/shop/customer/shop/{shopId}/goods` ➡️ 网关剥离 `/api` ➡️ 转发下游 `/shop/customer/shop/{shopId}/goods`。
* **B端请求路由路径**：`POST /api/shop/merchant/goods` ➡️ 网关剥离 `/api` ➡️ 转发下游 `/shop/merchant/goods`。
