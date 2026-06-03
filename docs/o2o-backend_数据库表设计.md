# o2o-backend 数据库表结构设计与建表 SQL (微服务已开发模块版)

本文档定义了 **智能 O2O 聚合电商平台** 的 MySQL 核心表结构及非关系型数据模型。
为了符合微服务**“独占数据库 (Database-per-Service)”**的架构规范，实现服务间物理数据隔离，我们为目前已开发/正在开发的服务设计了独立的数据库：
1.  **`o2o_user_db`**（由 `o2o-user-service` 独占访问）
2.  **`o2o_shop_db`**（由 `o2o-shop-service` 独占访问）

对于暂未开始设计的其他微服务模块（如交易模块、购物车模块），其 DDL 建表 SQL 和索引说明将在后续迭代开发到对应服务时再进行增补。

---

## 🗄️ 一、 建表 SQL 语句与字段说明

---

### 1. 用户微服务数据库 (`o2o_user_db`)

```sql
CREATE DATABASE IF NOT EXISTS `o2o_user_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `o2o_user_db`;
```

#### 1.0 数据库索引设计与作用说明
为保障微服务的高并发检索性能，并从底层拦截非法重复请求（如并发注册），本项目在用户库表中配置了如下索引体系：
*   **单列唯一索引 `idx_username` (`username`)** on `tb_user`：强制保障系统用户名登录主键级唯一，防止登录重名错乱。
*   **单列唯一索引 `idx_phone` (`phone`)** on `tb_user`：强制保障用户绑定手机号全局唯一，是防范同一手机号多次注册刷券套利的基础风控设施。
*   **单列普通检索索引 `idx_user_id` (`user_id`)** on `tb_address`：加快加载当前用户个人收货地址簿的查询速度。

#### 1.1 用户表 (`tb_user`)
保存用户核心登录及基础资料。

```sql
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID，自增主键',
  `username` varchar(50) NOT NULL COMMENT '用户名/登录账号（唯一）',
  `password` varchar(100) NOT NULL COMMENT '加密后的密码',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像链接',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：0-禁用，1-正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建/注册时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`),
  UNIQUE KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基础表';
```

#### 1.2 收货地址表 (`tb_address`)
包含用户的收货地址，以及对应的 LBS 经纬度信息（同城配送需计算距离）。

```sql
DROP TABLE IF EXISTS `tb_address`;
CREATE TABLE `tb_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID，主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人电话',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区/县',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `longitude` decimal(10,7) NOT NULL COMMENT '经度坐标 (LBS)',
  `latitude` decimal(10,7) NOT NULL COMMENT '纬度坐标 (LBS)',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认地址：0-否，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表';
```

---

### 2. 店铺与商品微服务数据库 (`o2o_shop_db`)

在店铺与商品搜索微服务中，我们并没有采用传统的“纯 MySQL”存储方案，而是设计了 **MySQL + Redis + MongoDB + Elasticsearch 混合存储架构**。

#### 2.0 数据库索引设计与作用说明
为保障微服务的高并发检索性能，并从底层拦截非法重复请求（如并发注册、重复评价），本项目在已开发服务的隔离库表中配置了多维度索引体系。
*   **联合唯一索引 `uni_order_goods` (`order_id`, `goods_id`)** on `tb_review`：物理层强幂等锁。确保一个订单中的单品只能被评价一次，拦截高并发重复提交，防范刷量。
*   **单列普通检索索引 `idx_category` (`category`)** on `tb_shop`：加快按分类（如美食、生鲜）筛选店铺的首页加载速度。
*   **单列普通检索索引 `idx_shop_id` (`shop_id`)** on `tb_goods`：加快店铺商品列表查询，防止进店时商品大表全表扫描。
*   **单列普通检索索引 `idx_goods_id` (`goods_id`)** on `tb_review`：支持按单品快速查询其下的所有评价列表。
*   **单列普通检索索引 `idx_shop_id` (`shop_id`)** on `tb_review`：支撑店铺评分汇总的高频聚合计算。
*   **单列普通检索索引 `idx_user_id` (`user_id`)** on `tb_review`：支持用户在个人中心高速调阅自己的历史评价列表。

#### 2.1 存储技术选型与痛点解决逻辑

##### 1) 痛点问题：传统“单 MySQL”方案下的瓶颈
*   **同城 LBS 检索慢**：传统 MySQL 在计算“附近 3 公里的商铺”时，必须对表中的经纬度字段使用复杂的三角函数（如余弦公式）进行计算过滤。由于无法走常规索引，每一次搜索都会引发**全表扫描和 CPU 暴涨**。
*   **海量大字段行溢出**：店铺评价中包含长篇大论、多张晒图。如果全部存入 MySQL 的 `TEXT` 或 `JSON` 字段，会导致数据**行溢出**。原本一个 16KB 物理页能存 300 条数据，现在只能存 2 条，导致磁盘随机 I/O 暴增，Buffer Pool 缓存瞬间失效。
*   **评价多维检索瘫痪**：用户在详情页经常需要检索“有图”、“差评”、“按时间排序”或输入关键字搜索评价。MySQL 建立过多联合索引会导致写入极慢，且模糊搜索（`LIKE '%充电快%'`）会导致全表扫描卡死。

##### 2) 解决方案：混合存储技术选型分工
为了解决上述瓶颈，我们对数据存储进行了如下专业分工：

*   **MySQL（核心元数据库）**：
    *   **负责内容**：仅存放店铺、商品、评价的最核心轻量级字段（如 ID、价格、评分、外键等）。
    *   **解决问题**：保证单行记录极窄，主键索引树小且常驻内存，确保基础 ACID 事务和主键查询在毫秒级内完成。
*   **Redis（高频 LBS 检索与评价统计）**：
    *   **负责内容**：商铺经纬度（Geo 结构）、店铺评论统计计数器。
    *   **解决问题**：利用 Redis Geo 空间索引，微秒级算出附近店铺 ID 列表，免除数据库三角函数运算，抗住高并发检索压力。
*   **MongoDB（非结构化文档大字段）**：
    *   **负责内容**：评价长文本、买家晒图 URL 数组、商家回复、追评明细。
    *   **解决问题**：将大字段从 MySQL 中剥离。MongoDB 采用 BSON 格式，天然适合存储 1 对多层级嵌套的复杂富文本，单次读写（Single Read/Write）吞吐量远超 MySQL，杜绝行溢出。
*   **Elasticsearch（全文搜索引擎）**：
    *   **负责内容**：扁平化汇总的评价检索索引（包含分词文本、有图/星级过滤状态位等）。
    *   **解决问题**：ES 通过**倒排索引**和二进制位图（Bitmaps）技术，零门槛搞定多维交叉筛选和中文分词模糊检索，前台读流量 100% 走 ES，把 MySQL 压力降为零。

---

#### 2.2 数据库初始化 DDL

```sql
CREATE DATABASE IF NOT EXISTS `o2o_shop_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `o2o_shop_db`;
```

#### 2.3 店铺表 (`tb_shop`)
商铺基础信息，包含经纬度，用于同城 LBS 距离排序。

**💡 字段设计考量与逻辑**：
*   `id` (自增主键)：作为 InnoDB 的聚簇索引，采用自增大整型能保证物理行数据顺序落盘写入，避免 B+ 树页分裂（Page Split），写性能最佳。
*   `category` (带 `idx_category` 索引)：前台用户高频按经营大类（如美食、生鲜）筛选，添加该索引能毫秒级定位目标类型，杜绝全表扫描。
*   `longitude` & `latitude` (`decimal(10,7)` 经纬度)：
    1. **为什么不用 float/double**：浮点数有二进制近似值偏差，无法保证金融级与配送地理位置的绝对精确度。
    2. **精度设计**：保留 7 位小数可将定位偏差控制在 **1.1 厘米** 级别，完全满足即时外卖配送的精准检索。
    3. **为什么存 MySQL 数据库**：作为持久化备份与“可信数据源”（Source of Truth）。在 Redis 缓存冷启动或发生故障崩溃重启后，用于执行**缓存预热（Cache Warm-up）**任务，重新将坐标读取并同步写入 Redis Geo。
*   `owner_id` (所属商家用户ID)：绑定商家账户，是防范商家横向越权操作（IDOR）的关键字段。后端在写入或修改商品时，将通过此字段强校验当前登录的 `userId` 与商铺的所有权关系。

```sql
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '店铺ID，主键',
  `name` varchar(100) NOT NULL COMMENT '店铺名称',
  `logo` varchar(255) DEFAULT NULL COMMENT '店铺 Logo 图片链接',
  `category` varchar(50) NOT NULL COMMENT '店铺分类，如：美食、便利店、数码、生鲜',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) NOT NULL COMMENT '店铺地址描述',
  `longitude` decimal(10,7) NOT NULL COMMENT '经度坐标 (LBS)',
  `latitude` decimal(10,7) NOT NULL COMMENT '纬度坐标 (LBS)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '营业状态：0-休息，1-营业中',
  `owner_id` bigint NOT NULL COMMENT '所属商家用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_owner_id` (`owner_id`),                -- 唯一索引：确保一个商家账号仅能持有一家店铺，彻底杜绝并发创建漏洞
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺基础表';
```

#### 2.4 商品表 (`tb_goods`)
店铺内的商品数据，以及物理库存。

**💡 字段设计考量与逻辑**：
*   `shop_id` (带 `idx_shop_id` 索引)：用户进入店铺详情页时，核心高频 SQL 为 `WHERE shop_id = ? AND status = 1`。如果不加索引，每次进店都会引发全平台的商品大表全表扫描。
*   `price` (`decimal(10,2)` 商品单价)：交易结算绝对不许使用 `float/double`（存在精度舍入漏洞）。使用 `decimal(10,2)` 能确保商品金额计算精确到“分”，账目一分不差。
*   `stock` (物理库存)：代表本地 MySQL 的实物底账。采用乐观锁扣减防御超卖。
*   `description` (图文描述 `text`)：用于商品图文介绍，内容极长。使用 `text` 字段由 MySQL 在页外以溢出页存储，保持主表单行宽度窄。由于图文详情**只有用户点击详情页时才按商品 ID 延时加载（懒加载）**，普通商品列表查询不需要查此字段，因此留在 MySQL 中使用本地事务同步更新（1:1强一致性约束），开发代价最小，且性能损耗可控。

```sql
DROP TABLE IF EXISTS `tb_goods`;
CREATE TABLE `tb_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID，主键',
  `shop_id` bigint NOT NULL COMMENT '所属店铺ID',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `price` decimal(10,2) NOT NULL COMMENT '商品单价 (元)',
  `stock` int NOT NULL DEFAULT '0' COMMENT '物理库存数量',
  `image` varchar(255) DEFAULT NULL COMMENT '商品图片主图',
  `description` text COMMENT '商品图文描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '上架状态：0-下架，1-上架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';
```

#### 2.5 商品评价表 (`tb_review`)
用户对订单内单品及店铺服务的评价，用于进行 Redis 高并发统计及 ES 检索。

> **⚠️ 渐进式架构方案（Approach A）说明**：
> 本系统目前采用**渐进式演进方案**。为了方便前期敏捷开发、降低单服务集成门槛、简化联调测试流程，我们将评价的长文本（`content text`）和晒图链接（`images json`）暂时保存在 MySQL 关系表内（MVP 快速交付）。在后续迭代中，系统将对该模块进行混合存储重构，届时这些大字段将剥离并完全迁移至持久化 Document 数据库 **MongoDB** 中，MySQL 表内仅保留关系主键与元数据。

**💡 字段设计考量与逻辑**：
*   `order_id` & `goods_id` (带联合唯一索引 `uni_order_goods`)：**大厂防刷单的第一道安全门槛**。强制绑定“订单+商品”的唯一性，实现物理层写入的强幂等控制。能自动拦截用户手抖产生的并发连击请求，且水军无法通过接口脚本对同一订单里的单品刷出多条评论，保护评价真实性。
*   `shop_id` (冗余店铺外键)：虽然可以通过 `goods_id` 关联出店铺，但冗余存储 `shop_id` 可以让我们在计算店铺整体好评率、星级等聚合指标时，直接 `WHERE shop_id = ?` 计算，**彻底消灭跨表联查**，实现高频聚合极速读。
*   `score` (评分 `tinyint`)：打分仅在 1~5 星范围内，使用 1 字节 of `tinyint` 相比 4 字节 of `int` 能在亿级数据量下省去数 GB 磁盘开销。
*   `images` (晒图 `json`)：利用 MySQL 8.0 的 json 类型存储图片链接数组（`["url1", "url2"]`），避免新建“图片子表”而导致的 1 对多关联查询，单表一次读取，极其轻量。
*   `status` (审核状态 `tinyint`)：用于合规治理。风控大模型或管理员发现违规评论时直接修改状态逻辑隐藏（`status=0`），不作物理删除以保留法务与追溯审计证据。

```sql
DROP TABLE IF EXISTS `tb_review`;
CREATE TABLE `tb_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID，自增主键',
  `order_id` bigint NOT NULL COMMENT '关联的订单ID（防刷单校验的核心依据）',
  `goods_id` bigint NOT NULL COMMENT '关联的商品ID（用于单品评价展示）',
  `shop_id` bigint NOT NULL COMMENT '关联的店铺ID（冗余字段，避免联表，加速店铺级评分聚合）',
  `user_id` bigint NOT NULL COMMENT '评价用户ID',
  `score` tinyint NOT NULL DEFAULT '5' COMMENT '评分：1至5星',
  `content` text COMMENT '文字评价内容（在超大规模系统下可剥离至MongoDB）',
  `images` json DEFAULT NULL COMMENT '晒图链接列表 (JSON 数组，如 ["url1", "url2"])',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态：0-隐藏/违规，1-正常显示，2-待审核',
  `is_anonymous` tinyint NOT NULL DEFAULT '0' COMMENT '是否匿名：0-公开，1-匿名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_order_goods` (`order_id`, `goods_id`), -- 联合唯一索引：确保一个订单中的单品只能被评价一次
  KEY `idx_goods_id` (`goods_id`),                       -- 商品维度检索索引
  KEY `idx_shop_id` (`shop_id`),                         -- 店铺维度聚合索引
  KEY `idx_user_id` (`user_id`)                          -- 用户维度历史查看索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表（基于订单链路版）';
```

---

## 🏗️ 二、 评价微服务非关系型存储结构设计 (NoSQL & Cache Schemas)

为了支撑商品评价系统的高并发读写、富媒体内容存放和多维模糊检索，除了关系型数据库 MySQL 外，本服务还采用了 MongoDB、Elasticsearch 和 Redis 共同协作，定义了以下非关系型数据库的存储模型。

### 1. MongoDB 文档存储结构 (`tb_review_content`)
用于存放评价的大文本、晒图数组、追评及商家回复等富文本非结构化数据。

```json
{
  "_id": "review_1289384792019482", 
  "review_id": 1289384792019482,            // 对应 MySQL tb_review 的 id
  "content": "这个充电宝非常轻便，充电速度很快，好评！",
  "media": {
    "images": [
      "https://cdn.o2o.com/images/review_1_1.jpg",
      "https://cdn.o2o.com/images/review_1_2.jpg"
    ],
    "video": "https://cdn.o2o.com/videos/review_1_1.mp4"
  },
  "append_review": {                         // 追评模块
    "content": "用了一个星期了，电池容量很真实，质量不错。",
    "days_after": 7,
    "images": ["https://cdn.o2o.com/images/append_1.jpg"]
  },
  "seller_reply": "亲亲，感谢您的好评，如有问题随时联系客服哦！"
}
```

### 2. Elasticsearch 评价检索索引结构 (`goods_reviews`)
用于承载前台用户对商品评价的复杂筛选（如带图、星级、差评过滤）及内容模糊搜索。

```json
{
  "mappings": {
    "properties": {
      "review_id": { "type": "long" },
      "goods_id": { "type": "long" },
      "score": { "type": "integer" },
      "content": { "type": "text", "analyzer": "ik_max_word" },  // 中文分词检索
      "has_image": { "type": "boolean" },                        // 用于快捷过滤“有图”
      "has_video": { "type": "boolean" },                        // 用于快捷过滤“有视频”
      "is_append": { "type": "boolean" },                        // 用于快捷过滤“有追评”
      "status": { "type": "integer" },                           // 评价状态（过滤违规/隐藏）
      "create_time": { "type": "date" }                          // 按时间排序
    }
  }
}
```

### 3. Redis 缓存与计数器存储结构

#### 3.1 店铺/商品评价统计计数器 (Hash 结构)
*   **Key 命名**: `o2o:goods:review:stats:{goodsId}`
*   **数据结构 (Hash)**:
    ```
    {
      "total_count": "1024",      // 评价总数
      "positive_count": "998",    // 好评数
      "negative_count": "10",     // 差评数
      "positive_rate": "97.4"     // 好评率百分比
    }
    ```

---

### 3. 购物车微服务数据库 (`o2o_cart_db`)

```sql
CREATE DATABASE IF NOT EXISTS `o2o_cart_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `o2o_cart_db`;
```

#### 3.0 数据库索引设计与作用说明
为保障高并发下的数据一致性与幂等性，本项目在购物车库中配置了以下索引：
*   **联合唯一索引 `uni_user_goods` (`user_id`, `goods_id`)** on `tb_cart`：保证单个用户对某件商品在数据库中只有唯一的一条购物车记录，防止并发加购产生多条重复数据，也是高并发同步双写的基础物理屏障。
*   **单列普通检索索引 `idx_user_id` (`user_id`)** on `tb_cart`：在缓存失效或加载购物车列表时，加快根据用户 ID 查询购物车全部商品记录的速度。

#### 3.1 购物车商品关联表 (`tb_cart`)
保存用户购物车的加购商品及对应数量。

```sql
DROP TABLE IF EXISTS `tb_cart`;
CREATE TABLE `tb_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车ID，自增主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `goods_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量',
  `selected` tinyint NOT NULL DEFAULT '1' COMMENT '是否选中：0-未选中，1-选中',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_user_goods` (`user_id`, `goods_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车商品关联表';
```
