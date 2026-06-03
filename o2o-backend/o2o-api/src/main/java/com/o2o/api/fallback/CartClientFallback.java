package com.o2o.api.fallback;

import com.o2o.api.CartClient;
import com.o2o.api.CartVo;
import com.o2o.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * CartClient Feign 降级兜底实现
 */
@Component
public class CartClientFallback implements CartClient {

    private static final Logger log = LoggerFactory.getLogger(CartClientFallback.class);

    @Override
    public Result<List<CartVo>> getSelectedCartItems() {
        log.warn("[Fallback] 购物车微服务异常或超时，获取选中商品列表自动降级为空列表。");
        return Result.ok(Collections.emptyList());
    }

    @Override
    public Result<?> deleteCartItems(List<Long> goodsIds) {
        log.warn("[Fallback] 购物车微服务异常或超时，批量删除购物车项降级失败。goodsIds: {}", goodsIds);
        return Result.fail("批量删除购物车失败");
    }
}
