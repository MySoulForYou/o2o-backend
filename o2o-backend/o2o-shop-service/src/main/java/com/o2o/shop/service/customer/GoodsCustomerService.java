package com.o2o.shop.service.customer;

import com.o2o.shop.entity.Goods;
import java.util.List;

public interface GoodsCustomerService {
    /**
     * 查询店铺下所有已上架的商品列表
     *
     * @param shopId 店铺ID
     * @return 商品列表
     */
    List<Goods> getGoodsByShopId(Long shopId);

    /**
     * 根据 ID 查询商品详情（带高并发缓存保护与店铺状态联动校验）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    Goods getGoodsById(Long id);
}
