package com.o2o.shop.service.merchant;

import com.o2o.shop.entity.Goods;

public interface GoodsMerchantService {
    /**
     * 商家端上架/添加商品
     *
     * @param goods 商品实体
     */
    void addGoods(Goods goods);
}
