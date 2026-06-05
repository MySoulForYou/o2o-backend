package com.o2o.trade.controller;

import com.o2o.common.Result;
import com.o2o.trade.service.OrderService;
import com.o2o.trade.vo.OrderCreateVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trade/customer")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建普通订单（从购物车结算已勾选的商品）
     *
     * @param createVo 结算收货人信息
     * @return 统一返回创建成功的订单 ID
     */
    @PostMapping("/order/create")
    public Result<Long> createOrder(@RequestBody OrderCreateVo createVo) {
        Long orderId = orderService.createOrder(createVo);
        return Result.ok(orderId);
    }

    /**
     * 秒杀抢单异步下单接口
     *
     * @param voucherId 秒杀代金券 ID
     * @return 统一返回预下单生成的排队订单 ID
     */
    @PostMapping("/seckill/order")
    public Result<Long> createSeckillOrder(@RequestParam("voucherId") Long voucherId) {
        Long orderId = orderService.createSeckillOrder(voucherId);
        return Result.ok(orderId);
    }

    /**
     * 轮询查询秒杀订单建单状态
     *
     * @param orderId 订单 ID
     * @return 状态码：9-处理中，0-待付款，-2-库存不足失败，其他-对应订单状态
     */
    @GetMapping("/seckill/status/{orderId}")
    public Result<Integer> getSeckillOrderStatus(@PathVariable("orderId") Long orderId) {
        Integer status = orderService.getSeckillOrderStatus(orderId);
        return Result.ok(status);
    }
}
