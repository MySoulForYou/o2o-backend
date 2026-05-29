package com.o2o.api.fallback;

import com.o2o.api.OrderClient;
import com.o2o.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OrderClient Feign 降级兜底实现
 */
@Component
public class OrderClientFallback implements OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClientFallback.class);

    @Override
    public Result<Boolean> verifyOrderForReview(Long orderId, Long goodsId) {
        log.warn("[Fallback] 交易微服务异常或超时，订单评价校验自动降级放行。orderId: {}, goodsId: {}", orderId, goodsId);
        // 大厂策略：远程校验作为辅助防护，当下游服务不可用时，降级允许用户评价，避免阻断核心用户体验。
        return Result.ok(false);
    }
}
