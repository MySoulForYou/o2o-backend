# o2o-backend 核心业务 API 接口设计文档

本文档定义了 **智能 O2O 聚合电商平台 (基础版)** 的前后端交互 API 接口契约。
所有请求都将经过统一网关（端口 `8080`），请求前缀统一为 `/api`。

---

## 🔒 统一响应格式 (JSON)

所有接口统一返回以下格式：

```json
{
  "code": 200,      // 状态码：200-成功，400-参数错误，401-未登录，403-无权限，500-系统异常
  "message": "操作成功",
  "data": { ... }   // 具体返回的业务数据（可以为 null 或 JSON 对象/数组）
}
```

---

## 👤 一、 用户与购物车微服务 (`user-service`)

### 1. 用户注册
*   **请求方式**：`POST`
*   **请求路径**：`/api/user/register`
*   **请求参数 (Body JSON)**：
    ```json
    {
      "username": "zhangsan",
      "password": "password123",
      "phone": "13800138000"
    }
    ```
*   **返回数据 (`data`)**：`null`

### 2. 用户登录
*   **请求方式**：`POST`
*   **请求路径**：`/api/user/login`
*   **请求参数 (Body JSON)**：
    ```json
    {
      "username": "zhangsan",
      "password": "password123"
    }
    ```
*   **返回数据 (`data`)**：
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsIn...", // JWT Token，后续请求需放入 Header "Authorization" 中
      "userInfo": {
        "id": 1,
        "username": "zhangsan",
        "nickname": "张三",
        "avatar": "http://example.com/avatar.png"
      }
    }
    ```

### 3. 获取收货地址列表 (需登录)
*   **请求方式**：`GET`
*   **请求路径**：`/api/user/address/list`
*   **返回数据 (`data` 数组)**：
    ```json
    [
      {
        "id": 10,
        "receiverName": "张三",
        "receiverPhone": "13800138000",
        "province": "广东省",
        "city": "深圳市",
        "district": "南山区",
        "detailAddress": "科兴科学园 A 栋",
        "longitude": 113.943567,
        "latitude": 22.548790,
        "isDefault": 1
      }
    ]
    ```

### 4. 添加购物车 (需登录，Redis Hash 存储)
*   **请求方式**：`POST`
*   **请求路径**：`/api/user/cart/add`
*   **请求参数 (Body JSON)**：
    ```json
    {
      "goodsId": 101,
      "quantity": 2
    }
    ```
*   **返回数据 (`data`)**：`null`

### 5. 查询购物车列表 (需登录)
*   **请求方式**：`GET`
*   **请求路径**：`/api/user/cart/list`
*   **返回数据 (`data` 数组)**：
    ```json
    [
      {
        "goodsId": 101,
        "goodsName": "小巧共享充电宝",
        "price": 2.50,
        "quantity": 2,
        "image": "http://img.com/goods.jpg"
      }
    ]
    ```

---

## 🏪 二、 店铺与商品微服务 (`shop-service`)

### 1. 检索附近店铺 (LBS 定位)
*   **请求方式**：`GET`
*   **请求路径**：`/api/shop/nearby`
*   **请求参数 (Query)**：
    *   `longitude` (必填): 用户当前经度，如 `113.943`
    *   `latitude` (必填): 用户当前纬度，如 `22.548`
    *   `radius` (选填): 搜索半径（米），默认 `3000`
    *   `category` (选填): 店铺分类筛选
*   **返回数据 (`data` 数组，按距离升序)**：
    ```json
    [
      {
        "id": 5,
        "name": "极速充电宝便利店",
        "logo": "http://img.com/shop.jpg",
        "category": "便利店",
        "distance": 320,  // 距离用户 320 米
        "address": "深圳市南山区科兴科学园B栋",
        "phone": "0755-123456"
      }
    ]
    ```

### 2. 查询店铺商品列表
*   **请求方式**：`GET`
*   **请求路径**：`/api/shop/{shopId}/goods`
*   **返回数据 (`data` 数组)**：
    ```json
    [
      {
        "id": 101,
        "name": "小巧共享充电宝",
        "price": 2.50,
        "stock": 99,
        "image": "http://img.com/goods.jpg",
        "description": "5000mAh 随身携带"
      }
    ]
    ```

### 3. 获取店铺评价汇总 (高并发 Redis 缓存接口)
*   **请求方式**：`GET`
*   **请求路径**：`/api/shop/{shopId}/reviews/summary`
*   **返回数据 (`data`)**：
    ```json
    {
      "shopId": 5,
      "averageScore": 4.8,     // 平均星级
      "totalCount": 248,       // 总评价数
      "latestReview": "送货非常快，充电宝很好用！"
    }
    ```

---

## 🛍️ 三、 交易与秒杀微服务 (`trade-service`)

### 1. 提交普通订单 (需登录)
*   **请求方式**：`POST`
*   **请求路径**：`/api/trade/order/create`
*   **请求参数 (Body JSON)**：
    ```json
    {
      "addressId": 10,
      "items": [
        {
          "goodsId": 101,
          "quantity": 2
        }
      ]
    }
    ```
*   **返回数据 (`data`)**：
    ```json
    {
      "orderId": 1289384792019482, // 分布式订单 ID
      "totalAmount": 5.00
    }
    ```

### 2. 秒杀下单抢购 (高并发 MQ 异步排队，需登录)
*   **请求方式**：`POST`
*   **请求路径**：`/api/trade/seckill/order`
*   **请求参数 (Body JSON)**：
    ```json
    {
      "goodsId": 201,        // 秒杀商品ID
      "addressId": 10
    }
    ```
*   **返回数据 (`data`)**：
    ```json
    {
      "status": "QUEUING",  // QUEUING-排队中，FAIL-排队失败/已售罄
      "goodsId": 201
    }
    ```

### 3. 轮询秒杀下单结果 (前端拿到 QUEUING 后循环调用)
*   **请求方式**：`GET`
*   **请求路径**：`/api/trade/seckill/result`
*   **请求参数 (Query)**：
    *   `goodsId` (必填)
*   **返回数据 (`data`)**：
    ```json
    {
      "status": "SUCCESS",            // SUCCESS-下单成功，QUEUING-排队中，FAIL-已售罄/下单失败
      "orderId": 1289384792019999     // 下单成功时返回订单 ID，否则为 null
    }
    ```
