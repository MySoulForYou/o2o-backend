package com.o2o.shop.controller.customer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.Result;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.service.customer.ShopCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/customer/shop")
public class ShopCustomerController {

    @Autowired
    private ShopCustomerService shopCustomerService;

    /**
     * 获取店铺详情接口
     *
     * @param id 店铺 ID
     * @return 统一返回店铺实体
     */
    @GetMapping("/{id}")
    public Result<Shop> getShopById(@PathVariable("id") Long id) {
        Shop shop = shopCustomerService.getShopById(id);
        return Result.ok(shop);
    }

    /**
     * 分页过滤查询店铺列表接口
     *
     * @param category 分类名称 (可选)
     * @param status 状态 (可选)
     * @param page 页码 (默认 1)
     * @param pageSize 每页显示条数 (默认 10)
     * @return 统一返回分页结果
     */
    @GetMapping("/page")
    public Result<Page<Shop>> pageShops(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Page<Shop> shopPage = shopCustomerService.pageShops(category, status, page, pageSize);
        return Result.ok(shopPage);
    }

    /**
     * 根据地理位置 (LBS) 分页查询附近店铺接口
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param radius 搜索半径 (单位：公里，默认 3.0)
     * @param category 分类过滤 (可选)
     * @param page 页码 (默认 1)
     * @param pageSize 每页显示条数 (默认 10)
     * @return 附近店铺分页结果 (按距离升序)
     */
    @GetMapping("/nearby")
    public Result<Page<Shop>> pageShopsNearby(
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam(value = "radius", defaultValue = "3.0") Double radius,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Page<Shop> shopPage = shopCustomerService.pageShopsNearby(longitude, latitude, radius, category, page, pageSize);
        return Result.ok(shopPage);
    }
}
