package com.o2o.shop.controller.merchant;

import com.o2o.common.Result;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.service.merchant.ShopMerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/merchant/shop")
public class ShopMerchantController {

    @Autowired
    private ShopMerchantService shopMerchantService;

    /**
     * 商家注册/创建店铺接口
     *
     * @param shop 店铺参数
     * @return 统一返回结果
     */
    @PostMapping
    public Result<Void> createShop(@RequestBody Shop shop) {
        shopMerchantService.createShop(shop);
        return Result.ok();
    }

    /**
     * 商家修改店铺信息接口
     *
     * @param shop 店铺参数
     * @return 统一返回结果
     */
    @PutMapping
    public Result<Void> updateShop(@RequestBody Shop shop) {
        shopMerchantService.updateShop(shop);
        return Result.ok();
    }

    /**
     * 获取当前登录商家名下的店铺详情接口
     *
     * @return 统一返回店铺详情
     */
    @GetMapping("/my")
    public Result<Shop> getMyShop() {
        Shop shop = shopMerchantService.getMyShop();
        return Result.ok(shop);
    }
}
