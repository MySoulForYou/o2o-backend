package com.o2o.shop.service.merchant;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.shop.entity.Goods;

public interface GoodsMerchantService {
    /**
     * 商家端上架/添加商品
     *
     * @param goods 商品实体
     */
    void addGoods(Goods goods);

    /**
     * 商家修改自己店铺的商品信息
     *
     * @param goods 商品实体
     */
    void updateGoods(Goods goods);

    /**
     * 商家分页查询自己店铺的商品列表（包含下架商品）
     *
     * @param page 页码
     * @param pageSize 每页显示条数
     * @return 商品分页结果
     */
    Page<Goods> pageMyGoods(Integer page, Integer pageSize);
}
