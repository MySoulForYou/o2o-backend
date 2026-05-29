package com.o2o.shop.service.customer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.shop.entity.Shop;

public interface ShopCustomerService {
    /**
     * 根据店铺 ID 查询店铺详情
     *
     * @param id 店铺 ID
     * @return 店铺实体
     */
    Shop getShopById(Long id);

    /**
     * 分页查询/筛选店铺列表
     *
     * @param category 分类名称 (可选)
     * @param status 状态 (可选)
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Page<Shop> pageShops(String category, Integer status, Integer page, Integer pageSize);
}
