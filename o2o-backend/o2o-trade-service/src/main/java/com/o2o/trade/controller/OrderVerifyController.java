package com.o2o.trade.controller;

import com.o2o.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trade/order")
public class OrderVerifyController {

    /**
     * 校验订单发表评价合法性接口（通过 Header 透传用户 ID）
     *
     * @param orderId 订单 ID
     * @param goodsId 商品 ID
     * @param userId  透传的用户 ID
     * @return 统一返回通过标志 Result.ok(true)
     */
     @GetMapping("/verify-for-review")
     public Result<Boolean> verifyOrderForReview(
             @RequestParam("orderId") Long orderId,
             @RequestParam("goodsId") Long goodsId,
             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
         System.out.println("====== OrderVerifyController: 接收到透传用户ID X-User-Id = " + userId + " ======");
         // 临时 Mock 阶段：默认所有订单均为该用户购买，且包含此商品，状态合法
         return Result.ok(true);
     }
}
