package com.o2o.api;

import com.o2o.api.fallback.GoodsClientFallback;
import com.o2o.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value = "o2o-shop-service", contextId = "goodsClient", fallback = GoodsClientFallback.class)
public interface GoodsClient {
    /**
     * 根据商品 ID 列表批量获取商品详情
     *
     * @param ids 商品 ID 列表
     * @return 商品 DTO 列表
     */
    @GetMapping("/shop/customer/shop/goods/batch")
    Result<List<GoodsDto>> getGoodsByIds(@RequestParam("ids") List<Long> ids);

    /**
     * 批量扣减商品物理库存
     *
     * @param deductList 扣减列表
     * @return 扣减结果
     */
    @PostMapping("/shop/customer/shop/goods/deduct-stock")
    Result<Boolean> deductStock(@RequestBody List<GoodsDeductStockDto> deductList);
}
