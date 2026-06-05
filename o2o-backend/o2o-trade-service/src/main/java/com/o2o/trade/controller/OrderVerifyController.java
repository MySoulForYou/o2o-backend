package com.o2o.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.common.Result;
import com.o2o.common.UserContext;
import com.o2o.trade.entity.Order;
import com.o2o.trade.entity.OrderItem;
import com.o2o.trade.mapper.OrderItemMapper;
import com.o2o.trade.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/trade/order")
public class OrderVerifyController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /**
     * 校验订单发表评价合法性接口（通过 Header 或 UserContext 获取用户 ID）
     *
     * @param orderId 订单 ID
     * @param goodsId 商品 ID
     * @param userIdHeader 透传的 X-User-Id 请求头
     * @return 校验通过返回 Result.ok(true)，否则返回 Result.ok(false)
     */
    @GetMapping("/verify-for-review")
    public Result<Boolean> verifyOrderForReview(
            @RequestParam("orderId") Long orderId,
            @RequestParam("goodsId") Long goodsId,
            @RequestHeader(value = "X-User-Id", required = false) Long userIdHeader) {
        
        // 优先使用拦截器提取的 ThreadLocal 中的 userId
        Long userId = UserContext.getUserId();
        if (userId == null) {
            userId = userIdHeader;
        }

        if (userId == null) {
            log.warn("评价校验失败：用户未登录");
            return Result.ok(false);
        }

        log.info("开始校验评价合法性, userId: {}, orderId: {}, goodsId: {}", userId, orderId, goodsId);

        // 1. 查询订单是否存在、是否属于当前用户、并且状态已付款或完成 (status: 1, 2, 3)
        LambdaQueryWrapper<Order> orderQw = new LambdaQueryWrapper<>();
        orderQw.eq(Order::getId, orderId)
               .eq(Order::getUserId, userId)
               .in(Order::getStatus, 1, 2, 3); // 已付款, 配送中, 已完成
        
        Order order = orderMapper.selectOne(orderQw);
        if (order == null) {
            log.warn("评价校验失败：未查找到对应的有效付款订单. orderId: {}, userId: {}", orderId, userId);
            return Result.ok(false);
        }

        // 2. 查询订单中是否包含了要评价的商品项
        LambdaQueryWrapper<OrderItem> itemQw = new LambdaQueryWrapper<>();
        itemQw.eq(OrderItem::getOrderId, orderId)
              .eq(OrderItem::getGoodsId, goodsId);
        
        Long itemCount = orderItemMapper.selectCount(itemQw);
        if (itemCount == null || itemCount <= 0) {
            log.warn("评价校验失败：订单中不包含该商品. orderId: {}, goodsId: {}", orderId, goodsId);
            return Result.ok(false);
        }

        log.info("评价合法性校验成功, userId: {}, orderId: {}, goodsId: {}", userId, orderId, goodsId);
        return Result.ok(true);
    }
}
