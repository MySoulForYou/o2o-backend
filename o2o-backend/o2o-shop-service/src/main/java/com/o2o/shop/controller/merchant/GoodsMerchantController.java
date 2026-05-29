package com.o2o.shop.controller.merchant;

import com.o2o.common.Result;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.service.merchant.GoodsMerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/merchant/goods")
public class GoodsMerchantController {

    @Autowired
    private GoodsMerchantService goodsMerchantService;

    /**
     * 商家添加/上架商品接口
     *
     * @param goods 商品参数体
     * @return 统一返回成功结果
     */
    @PostMapping
    public Result<Void> addGoods(@RequestBody Goods goods) {
        goodsMerchantService.addGoods(goods);
        return Result.ok();
    }
}
