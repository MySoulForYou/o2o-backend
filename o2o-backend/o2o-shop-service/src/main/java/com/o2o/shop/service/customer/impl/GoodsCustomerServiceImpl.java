package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.common.BusinessException;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.GoodsCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsCustomerServiceImpl implements GoodsCustomerService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Override
    public List<Goods> getGoodsByShopId(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("店铺 ID 不能为空");
        }

        // 联动校验店铺状态：非营业中（或已下线/不存在）的店铺，C端禁止展示其商品
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "目标店铺不存在");
        }
        if (shop.getStatus() == null || shop.getStatus() != 1) {
            throw new BusinessException(400, "该店铺已暂停营业");
        }

        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Goods::getShopId, shopId)
                    .eq(Goods::getStatus, 1) // C端只查询上架商品
                    .orderByDesc(Goods::getCreateTime);
        return goodsMapper.selectList(queryWrapper);
    }
}
