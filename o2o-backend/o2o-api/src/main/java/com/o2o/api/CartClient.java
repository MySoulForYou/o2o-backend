package com.o2o.api;

import com.o2o.api.fallback.CartClientFallback;
import com.o2o.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "o2o-cart-service", contextId = "cartClient", fallback = CartClientFallback.class)
public interface CartClient {

    /**
     * 获取当前用户已勾选的购物车商品详情列表
     *
     * @return 选中商品详情列表
     */
    @GetMapping("/cart/list/selected")
    Result<List<CartVo>> getSelectedCartItems();

    /**
     * 批量删除购物车中的商品项
     *
     * @param goodsIds 商品 ID 列表
     * @return 统一返回结果
     */
    @DeleteMapping("/cart/delete/batch")
    Result<?> deleteCartItems(@RequestParam("goodsIds") List<Long> goodsIds);
}
