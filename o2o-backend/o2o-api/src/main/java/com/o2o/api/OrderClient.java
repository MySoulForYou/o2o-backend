package com.o2o.api;

import com.o2o.api.fallback.OrderClientFallback;
import com.o2o.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "o2o-trade-service", contextId = "orderClient", fallback = OrderClientFallback.class)
public interface OrderClient {
    /**
     * 评价合法性远程校验接口（通过 Header 透传用户 ID）
     *
     * @param orderId 订单 ID
     * @param goodsId 商品 ID
     * @return 校验通过返回 true，否则返回 false
     */
     @GetMapping("/trade/order/verify-for-review")
     Result<Boolean> verifyOrderForReview(
             @RequestParam("orderId") Long orderId,
             @RequestParam("goodsId") Long goodsId);
}
