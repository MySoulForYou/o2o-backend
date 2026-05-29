package com.o2o.shop.service.merchant.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.BusinessException;
import com.o2o.common.UserContext;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.merchant.GoodsMerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsMerchantServiceImpl implements GoodsMerchantService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addGoods(Goods goods) {
        // 1. 获取当前登录的商家用户 ID
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        // 2. 校验商铺归属关系，防止越权
        if (goods.getShopId() == null) {
            // 如果前端未传 shopId，后端自动根据当前登录用户 ID 查找店铺
            LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Shop::getOwnerId, currentUserId).last("LIMIT 1");
            Shop shop = shopMapper.selectOne(queryWrapper);
            if (shop == null) {
                throw new BusinessException("当前商家账号未绑定任何店铺，无法上架商品");
            }
            goods.setShopId(shop.getId());
        } else {
            // 如果前端传了 shopId，强行校验当前商家是否是该店铺的所有者
            Shop shop = shopMapper.selectById(goods.getShopId());
            if (shop == null) {
                throw new BusinessException("目标店铺不存在");
            }
            if (!shop.getOwnerId().equals(currentUserId)) {
                // 拦截越权请求
                throw new BusinessException(403, "越权操作：您无权向非自家的店铺添加商品");
            }
        }

        // 3. 设置默认值并上架商品
        if (goods.getStatus() == null) {
            goods.setStatus(1); // 默认直接上架
        }
        goodsMapper.insert(goods);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGoods(Goods goods) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        if (goods.getId() == null) {
            throw new BusinessException("商品 ID 不能为空");
        }

        // 校验目标商品是否存在
        Goods existingGoods = goodsMapper.selectById(goods.getId());
        if (existingGoods == null) {
            throw new BusinessException(404, "目标商品不存在");
        }

        // 校验该商品所属的店铺是否归属当前商家
        Shop shop = shopMapper.selectById(existingGoods.getShopId());
        if (shop == null) {
            throw new BusinessException(404, "商品所属店铺不存在");
        }
        if (!shop.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(403, "越权操作：您无权修改非自家店铺的商品");
        }

        // 锁定 shopId 属性，禁止通过修改接口变动商品的店铺归属
        goods.setShopId(null);
        goodsMapper.updateById(goods);
    }

    @Override
    public Page<Goods> pageMyGoods(Integer page, Integer pageSize) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        // 根据当前登录用户查询其拥有的店铺
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getOwnerId, currentUserId).last("LIMIT 1");
        Shop shop = shopMapper.selectOne(queryWrapper);
        if (shop == null) {
            throw new BusinessException("当前商家账号未绑定任何店铺，无法查询商品列表");
        }

        // 分页查询该店铺下的所有商品（包括上架和下架商品）
        Page<Goods> goodsPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Goods> goodsQuery = new LambdaQueryWrapper<>();
        goodsQuery.eq(Goods::getShopId, shop.getId())
                  .orderByDesc(Goods::getCreateTime);

        return goodsMapper.selectPage(goodsPage, goodsQuery);
    }
}
