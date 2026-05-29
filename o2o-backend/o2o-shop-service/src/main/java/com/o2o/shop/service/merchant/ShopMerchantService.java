package com.o2o.shop.service.merchant;

import com.o2o.shop.entity.Shop;

public interface ShopMerchantService {
    /**
     * 商家创建自己的店铺
     *
     * @param shop 店铺参数
     */
    void createShop(Shop shop);

    /**
     * 商家更新自己的店铺信息
     *
     * @param shop 店铺参数
     */
    void updateShop(Shop shop);

    /**
     * 获取当前登录商家名下的店铺详情
     *
     * @return 店铺详情，如果未创建店铺则返回 null
     */
    Shop getMyShop();
}
