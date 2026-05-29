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
│   │   ├── customer                # C端消费者接口 (RequestMapping: /shop/customer/**)
│   │   │   ├── GoodsCustomerController.java   (获取指定店铺上架商品)
│   │   │   ├── ShopCustomerController.java    (获取店铺详情/分页筛选列表)
│   │   │   └── ReviewCustomerController.java  (消费者发表/分页查看商品评价)
│   │   └── merchant                # B端商家管理接口 (RequestMapping: /shop/merchant/**)
│   │       ├── GoodsMerchantController.java  (商家添加与上架商品)
│   │       └── ShopMerchantController.java   (商家创建与更新店铺信息)
│   │
│   ├── service
│   │   ├── customer                # C端只读/操作业务逻辑
│   │   │   ├── GoodsCustomerService.java & impl
│   │   │   ├── ShopCustomerService.java & impl
│   │   │   └── ReviewCustomerService.java & impl
│   │   └── merchant                # B端商家写业务逻辑 (防越权校验核心)
│   │       ├── GoodsMerchantService.java & impl
│   │       └── ShopMerchantService.java & impl
│   │
│   ├── entity                      # 实体对象层 (Shop.java, Goods.java, Review.java)
│   ├── vo                          # 视图传输对象层 (ReviewVo.java)
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

### 3.2 最终防越权校验逻辑与分布式 Header 传递
我们选择**“网关统一鉴权 + 头部信息透传 (Header Propagation)”**的安全模型：
1. **网关透传**：网关验证 Token 合法后，提取 `userId`，并在请求头中隐式注入 `X-User-Id`。
2. **拦截器装配**：`o2o-shop-service` 注册 `UserContextInterceptor`，自动提取该头并写入 `ThreadLocal` 的 `UserContext` 中，请求结束时自动清理，防止线程复用污染。
3. **分布式 Header 传递**：当 `o2o-shop-service` 需要通过 Feign 远程调用下游微服务时，由于 Feign 作为一个独立的 HTTP 客户端会丢失原始 Header，系统注册了 `FeignHeaderInterceptor`，在 RPC 请求发出前自动获取当前线程上下文 `UserContext` 里的 `X-User-Id` 并注入到 Feign 的请求头中，完成全调用链路的身份信息零感知自动透传。
4. **业务层强校验**：
   - **未传 shopId**：后端自动执行 `SELECT id FROM tb_shop WHERE owner_id = currentUserId`，将查出的 `shopId` 强制赋值给商品，防止商家随意挂靠。
   - **传了 shopId**：后端获取店铺元数据后校验 `shop.getOwnerId().equals(currentUserId)`。一旦不匹配，立刻抛出 `403 Forbidden` 异常。

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

* **C端请求路由路径**：`GET /api/shop/customer/**` ➡️ 网关剥离 `/api` ➡️ 转发下游 `/shop/customer/**`。
* **B端请求路由路径**：`POST/PUT /api/shop/merchant/**` ➡️ 网关剥离 `/api` ➡️ 转发下游 `/shop/merchant/**`。

---

## 🏪 5. 店铺与评价基础 CRUD 接口规范

### 5.1 店铺管理接口 (Shop API)

#### 1) 【B端】商家创建店铺
*   **接口路径**：`POST /shop/merchant/shop`
*   **接口说明**：商家创建自己的店铺，后端获取当前登录用户 ID 并绑定为 `owner_id`（限制一商家账号仅能持有一家店铺，避免重复注册）。
*   **请求参数示例** (JSON Body)：
    ```json
    {
      "name": "极客数码专营店",
      "logo": "https://cdn.o2o.com/logos/shop1.png",
      "category": "数码",
      "phone": "13800138000",
      "address": "北京市朝阳区科创大厦A座101",
      "longitude": 116.481028,
      "latitude": 39.996794
    }
    ```

#### 2) 【B端】商家更新店铺信息
*   **接口路径**：`PUT /shop/merchant/shop`
*   **接口说明**：商家修改名下店铺信息，强校验所有权关系。禁止变更 `owner_id` 字段以防所有权跨商家跨域漂移。
*   **请求参数示例** (JSON Body)：
    ```json
    {
      "id": 1,
      "name": "极客数码旗舰店",
      "logo": "https://cdn.o2o.com/logos/shop1_new.png",
      "category": "数码",
      "phone": "13800138001",
      "address": "北京市朝阳区科创大厦A座101室",
      "longitude": 116.481028,
      "latitude": 39.996794,
      "status": 1
    }
    ```

#### 3) 【C端】消费者查询店铺详情
*   **接口路径**：`GET /shop/customer/shop/{id}`
*   **接口说明**：面向消费者展示店铺的基本元数据。

#### 4) 【C端】消费者分页筛选店铺列表
*   **接口路径**：`GET /shop/customer/shop/page`
*   **请求参数** (Query Params)：
    - `category` (String, 可选)：店铺经营分类
    - `status` (Integer, 可选)：营业状态 (0-休息, 1-营业中)
    - `page` (Integer, 默认1)：当前页码
    - `pageSize` (Integer, 默认10)：每页条数

#### 5) 【B端】获取当前登录商家的店铺详情
*   **接口路径**：`GET /shop/merchant/shop/my`
*   **接口说明**：商家获取自己绑定的店铺配置详情（未创建店铺则抛出 404）。

---

### 5.2 评价管理接口 (Review API)

#### 1) 【C端】消费者发表评价（订单防重）
*   **接口路径**：`POST /shop/customer/review`
*   **接口说明**：消费者对已购买订单内的某件单品发起评价。
*   **请求参数示例** (JSON Body)：
    ```json
    {
      "orderId": 10086,
      "goodsId": 12,
      "score": 5,
      "content": "充电宝非常小巧，质量很好，确实快充！",
      "images": "[\"https://cdn.o2o.com/reviews/img1.jpg\"]",
      "isAnonymous": 0
    }
    ```

#### 2) 【C端】消费者分页查看商品下属的评价列表
*   **接口路径**：`GET /shop/customer/review/goods/{goodsId}`
*   **请求参数** (Query Params)：
    - `page` (Integer, 默认1)
    - `pageSize` (Integer, 默认10)
*   **出参包装说明**：返回对象为 `ReviewVo` 列表，包含从主表带出的 `username`。

---

### 5.3 商品管理接口 (Goods API)

#### 1) 【B端】商家修改商品信息
*   **接口路径**：`PUT /shop/merchant/goods`
*   **接口说明**：商家修改自己店铺下某款商品的名称、单价、库存、主图、详情描述或上下架状态（强校验商品店铺所有权，拦截越权修改，限制 `shopId` 的修改）。

#### 2) 【B端】商家分页查看自家店铺商品列表
*   **接口路径**：`GET /shop/merchant/goods/page`
*   **接口说明**：商家登录后，分页获取自己店铺的所有商品（包含已上架与已下架状态的商品，供后台展示管理）。
*   **请求参数** (Query Params)：
    - `page` (Integer, 默认1)：当前页码
    - `pageSize` (Integer, 默认10)：每页条数

---

## 🛡️ 6. 核心业务与安全防刷底盘实现

为了确保系统的高可靠和抗刷单防越权设计，系统在核心业务层落地了以下安全控制：

### 6.1 B端店铺越权强防御逻辑
- 在商家更新店铺配置时，`ShopMerchantServiceImpl` 会首先调用 `shopMapper.selectById(shop.getId())` 查询数据库源。
- 随后通过比较 `existingShop.getOwnerId().equals(currentUserId)` 来校验身份所有权。
- 若校验不通过，直接抛出 `403 Forbidden` 级别的 `BusinessException`。此外，强行将更新实体的 `ownerId` 置为 `null`，封死越权窃取店铺或转移所有权的漏洞。
- **店铺并发创建的唯一性物理防线**：在商家创建店铺时，为了防范并发网络重试或恶意高并发刷接口绕过 Java 的 `selectCount` 前置校验，我们在数据库底盘对 `tb_shop` 表的 `owner_id` 字段建立了 **`UNIQUE KEY uni_owner_id(owner_id)` 唯一索引**。Java 业务层（`ShopMerchantServiceImpl.createShop`）通过捕获 `DuplicateKeyException` 异常并包装为友好业务异常抛出，彻底杜绝单商家账号持有多个店铺的并发竞态漏洞。

### 6.2 评价隐式多重注入与数据库硬防刷
- 消费者发起评价时，`ReviewCustomerServiceImpl` 从网关拦截器透传的上下文隐式获取当前真实的 `userId`，绝不以请求体中传入的 `userId` 为准，防止篡改他人账号发帖。
- 业务层通过商品主键 `goodsId` 直查 `tb_goods` 自动反查并补充写入 `shopId`，确保系统冗余字段的 100% 正确性，避免由于前端伪造店铺 ID 导致的商铺数据偏斜。
- **数据库幂等性控制**：利用物理联合唯一索引 `uni_order_goods(order_id, goods_id)` 形成硬防线。如果因为客户端重复点击或网络重试导致二次写入请求，Java 层通过捕获底层 Spring 的 `DuplicateKeyException` 并友好包装，向外部抛出可读的异常，保障数据一致性。

### 6.3 消费者匿名评价脱敏处理
- 在商品评价分页查询中，业务层会检索符合条件的 `Review` 列表。
- 如果某条记录的字段 `isAnonymous == 1`（匿名评价），后端处理模块会强制将视图对象中的 `username` 隐藏输出为 `用***`；若为公开评价，则默认拼装并返回带有 `用户_{userId}` 格式的遮罩名称，在保护消费者隐私的同时，提升接口数据格式的可解析性。

### 6.4 消费者端商品展示的店铺状态联动校验
- **业务背景**：C端消费者在浏览某店铺商品时，如果该店铺已经暂停营业（`status != 1`）或不存在，继续展示其商品甚至允许下单将引发严重业务纠纷。
- **校验逻辑**：在 `GoodsCustomerServiceImpl.getGoodsByShopId` 方法中，不仅校验商品是否上架，而且引入对所属店铺（`Shop`）的联动检索：
  1. 首先根据 `shopId` 查询 `tb_shop` 详情。
  2. 若店铺不存在，抛出 `404` 异常（提示“目标店铺不存在”）。
  3. 若店铺的 `status` 字段值不为 `1`（营业中），抛出 `400` 异常（提示“该店铺已暂停营业”），从业务源头实施前置状态拦截。

---

## 7. 微服务间 RPC 容错降级与 Header 隐式透传设计

为了保障在分布式高并发场景下系统的高可用性和容错防雪崩能力，店铺服务引入了**大厂级的统一 Feign Fallback 降级**与 **Header 无感透传** 架构：

### 7.1 分布式 Header 隐式透传与参数简化
- **Header 自动注入**：通过注册 `FeignHeaderInterceptor`（实现 `RequestInterceptor`），在每次 Feign 发出远程 RPC 请求前，拦截器会自动从当前线程上下文 `UserContext`（由网关在入口提取并由前端网关拦截器放入 `ThreadLocal`）中获取 `X-User-Id`，并自动注入到 Feign 的 HTTP 请求头中。
- **服务内无感接收**：接口参数无需显式在 RPC URL 请求参数中传递 `userId`，下游微服务（如交易服务 `o2o-trade-service`）的 `OrderVerifyController` 直接使用 Spring Boot 的 `@RequestHeader(value = "X-User-Id", required = false) Long userId` 即可接收。这保证了微服务接口定义的干净度与防篡改安全性。

### 7.2 统一 Fallback 降级与容错兜底
在微服务环境中，下游服务的临时网络抖动或崩溃（如 `o2o-trade-service` 服务未开启/Nacos 未注册导致的 503 异常）如果不作处理，会导致接口直接 500。我们对此设计了双重防护：

#### 1) 订单校验降级放行（宽容策略）
- **Feign 契约层降级**：`OrderClient` 关联 `OrderClientFallback.class`。当下游交易服务发生熔断、超时或 503 时，Feign 会自动切换到降级类，打印警告日志并默认返回 `Result.ok(true)`。
- **业务层防御性捕获**：在 `ReviewCustomerServiceImpl` 评价创建入口中，对 `verifyOrderForReview` 进行 `try-catch` 包裹，若 RPC 发生未被代理拦截的致命错误时，自动兜底通过（宽容降级放行），保障评价发表（写操作）这一核心用户体验的绝对不中断。

#### 2) 用户数据查询降级（遮罩脱敏）
- **批量查询降级**：`UserClient` 关联 `UserClientFallback.class`。当用户服务异常宕机时，批量查询用户信息自动降级返回 `Result.ok(Collections.emptyList())`（空列表）。
- **平滑展示**：配合业务层的数据遮罩逻辑，若未查到用户真实昵称，评价列表会自动采用 `用户_{userId}` 的遮罩格式展示，使得用户服务崩溃不影响整个评价列表（读操作）的正常渲染。
