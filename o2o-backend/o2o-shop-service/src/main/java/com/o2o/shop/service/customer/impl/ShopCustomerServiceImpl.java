package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.BusinessException;
import com.o2o.shop.cache.CacheClient;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.ShopCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ShopCustomerServiceImpl implements ShopCustomerService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Shop getShopById(Long id) {
        // 使用工具类封装好的【逻辑过期 + 异步重建】策略读取缓存
        Shop shop = cacheClient.queryWithLogicalExpire(
                "o2o:shop:detail:",       // 1. Redis Key 前缀
                id,                       // 2. 查询的主键ID
                Shop.class,               // 3. 目标反序列化类型
                shopMapper::selectById,   // 4. 查数据库的回调函数 (直接返回 null 以让 CacheClient 缓存空值防穿透)
                30L,                      // 5. 逻辑过期时间：30
                TimeUnit.MINUTES,         // 6. 逻辑过期时间单位：分钟
                "o2o:lock:shop:rebuild:"  // 7. 重建时抢分布式锁的 Key 前缀
        );
        if (shop == null) {
            throw new BusinessException(404, "目标店铺不存在");
        }
        return shop;
    }

    @Override
    public Page<Shop> pageShops(String category, Integer status, Integer page, Integer pageSize) {
        Page<Shop> shopPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();

        if (category != null && !category.trim().isEmpty()) {
            queryWrapper.eq(Shop::getCategory, category.trim());
        }

        if (status != null) {
            queryWrapper.eq(Shop::getStatus, status);
        }

        // 默认按创建时间降序排序
        queryWrapper.orderByDesc(Shop::getCreateTime);

        return shopMapper.selectPage(shopPage, queryWrapper);
    }
}
