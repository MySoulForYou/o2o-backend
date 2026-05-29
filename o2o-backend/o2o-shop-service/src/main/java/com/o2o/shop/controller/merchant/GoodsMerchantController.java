package com.o2o.shop.controller.merchant;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.Result;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.service.merchant.GoodsMerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 商家更新商品信息接口
     *
     * @param goods 商品参数体
     * @return 统一返回成功结果
     */
    @PutMapping
    public Result<Void> updateGoods(@RequestBody Goods goods) {
        goodsMerchantService.updateGoods(goods);
        return Result.ok();
    }

    /**
     * 商家分页查看自家店铺商品列表接口（包含下架商品）
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<Page<Goods>> pageMyGoods(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Page<Goods> goodsPage = goodsMerchantService.pageMyGoods(page, pageSize);
        return Result.ok(goodsPage);
    }
}
