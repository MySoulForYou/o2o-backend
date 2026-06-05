package com.o2o.trade.service;

import com.o2o.trade.vo.OrderCreateVo;

public interface OrderService {

    /**
     * 创建普通订单（结算购物车中已勾选的商品）
     *
     * @param createVo 订单收货人信息
     * @return 创建成功的订单 ID
     */
    Long createOrder(OrderCreateVo createVo);

    /**
     * 秒杀抢单异步下单接口
     *
     * @param voucherId 秒杀代金券 ID
     * @return 预扣库存成功后生成的分布式订单 ID (Snowflake)
     */
    Long createSeckillOrder(Long voucherId);

    /**
     * 轮询查询秒杀订单建单状态
     *
     * @param orderId 订单 ID
     * @return 状态码：9-处理中，0-排队成功待付款，-2-无货建单失败，其他-订单对应状态
     */
    Integer getSeckillOrderStatus(Long orderId);
}
