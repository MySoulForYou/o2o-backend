package com.o2o.shop.controller.customer;

import com.o2o.common.Result;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.service.customer.GoodsCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop/customer/shop")
public class GoodsCustomerController {

    @Autowired
    private GoodsCustomerService goodsCustomerService;

    /**
     * 获取指定店铺下所有已上架的商品列表
     *
     * @param shopId 店铺ID
     * @return 商品列表
     */
    @GetMapping("/{shopId}/goods")
    public Result<List<Goods>> getShopGoods(@PathVariable("shopId") Long shopId) {
        List<Goods> goodsList = goodsCustomerService.getGoodsByShopId(shopId);
        return Result.ok(goodsList);
    }

    /**
     * 根据商品 ID 获取单品详情接口
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/goods/{id}")
    public Result<Goods> getGoodsById(@PathVariable("id") Long id) {
        Goods goods = goodsCustomerService.getGoodsById(id);
        return Result.ok(goods);
    }
}
