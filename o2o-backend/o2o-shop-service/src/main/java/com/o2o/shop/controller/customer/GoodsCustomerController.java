package com.o2o.shop.controller.customer;

import com.o2o.api.GoodsDeductStockDto;
import com.o2o.common.Result;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.service.customer.GoodsCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 根据商品 ID 列表批量获取商品详情接口
     *
     * @param ids 商品 ID 列表
     * @return 商品列表
     */
    @GetMapping("/goods/batch")
    public Result<List<Goods>> getGoodsByIds(@RequestParam("ids") List<Long> ids) {
        List<Goods> goodsList = goodsCustomerService.getGoodsByIds(ids);
        return Result.ok(goodsList);
    }

    /**
     * 批量扣减商品物理库存
     *
     * @param deductList 扣减库存列表
     * @return 是否成功
     */
    @PostMapping("/goods/deduct-stock")
    public Result<Boolean> deductStock(@RequestBody List<GoodsDeductStockDto> deductList) {
        boolean success = goodsCustomerService.deductStock(deductList);
        return Result.ok(success);
    }
}
