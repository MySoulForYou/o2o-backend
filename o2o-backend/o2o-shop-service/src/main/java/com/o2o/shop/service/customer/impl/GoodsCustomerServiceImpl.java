package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.service.customer.GoodsCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsCustomerServiceImpl implements GoodsCustomerService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Override
    public List<Goods> getGoodsByShopId(Long shopId) {
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Goods::getShopId, shopId)
                    .eq(Goods::getStatus, 1) // C端只查询上架商品
                    .orderByDesc(Goods::getCreateTime);
        return goodsMapper.selectList(queryWrapper);
    }
}
