package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.BusinessException;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.ShopCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ShopCustomerServiceImpl implements ShopCustomerService {

    @Autowired
    private ShopMapper shopMapper;

    @Override
    public Shop getShopById(Long id) {
        Shop shop = shopMapper.selectById(id);
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
