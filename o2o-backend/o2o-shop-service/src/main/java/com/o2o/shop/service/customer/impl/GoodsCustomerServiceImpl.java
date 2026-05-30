package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.o2o.common.BusinessException;
import com.o2o.shop.cache.CacheClient;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.GoodsCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GoodsCustomerServiceImpl implements GoodsCustomerService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public List<Goods> getGoodsByShopId(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("店铺 ID 不能为空");
        }

        // 联动校验店铺状态：改走 CacheClient 的逻辑过期缓存读取，防止高并发直接击穿数据库
        Shop shop = cacheClient.queryWithLogicalExpire(
                "o2o:shop:detail:",
                shopId,
                Shop.class,
                shopMapper::selectById, // 返回 null 以让 CacheClient 缓存空值防御穿透
                30L,
                TimeUnit.MINUTES,
                "o2o:lock:shop:rebuild:"
        );
        if (shop == null) {
            throw new BusinessException(404, "目标店铺不存在");
        }
        if (shop.getStatus() == null || shop.getStatus() != 1) {
            throw new BusinessException(400, "该店铺已暂停营业");
        }

        // 使用 CacheClient 自动防穿透、防雪崩以及高并发击穿的缓存读写
        return cacheClient.queryWithMutex(
                "o2o:shop:goods:",
                shopId,
                new TypeReference<List<Goods>>() {}, // 传入带 Goods 泛型的 TypeReference
                id -> {
                    // 查询DB回调
                    LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(Goods::getShopId, id)
                                .eq(Goods::getStatus, 1) // C端只查询上架商品
                                .orderByDesc(Goods::getCreateTime);
                    return goodsMapper.selectList(queryWrapper);
                },
                30L, // 基础物理过期时间 30 分钟
                TimeUnit.MINUTES,
                "o2o:lock:goods:rebuild:" // 互斥锁 Key 前缀
        );
    }

    @Override
    public Goods getGoodsById(Long id) {
        if (id == null) {
            throw new BusinessException("商品 ID 不能为空");
        }

        // 使用互斥锁查询单品详情，启用物理 TTL 抖动和看门狗自动延时，防止缓存雪崩/穿透/击穿
        Goods goods = cacheClient.queryWithMutex(
                "o2o:goods:detail:",
                id,
                new TypeReference<Goods>() {},
                goodsMapper::selectById, // 数据库回调：仅查询商品，返回 null 以便 CacheClient 缓存空值
                30L, // 基础生存时间 30 分钟，自动加 0~300秒 随机抖动
                TimeUnit.MINUTES,
                "o2o:lock:goods:detail:rebuild:"
        );

        if (goods == null) {
            throw new BusinessException(404, "目标商品不存在");
        }

        // 联动校验店铺状态：已暂停营业或已下线的店铺，C端商品详情亦禁止展示
        Shop shop = cacheClient.queryWithLogicalExpire(
                "o2o:shop:detail:",
                goods.getShopId(),
                Shop.class,
                shopMapper::selectById, // 数据库回调：仅查询店铺，返回 null 以让 CacheClient 缓存空值
                30L,
                TimeUnit.MINUTES,
                "o2o:lock:shop:rebuild:"
        );

        if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
            throw new BusinessException(400, "该商品所属店铺已暂停营业或不存在");
        }

        return goods;
    }
}
