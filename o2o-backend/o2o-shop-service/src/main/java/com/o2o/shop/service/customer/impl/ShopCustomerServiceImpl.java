package com.o2o.shop.service.customer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.BusinessException;
import com.o2o.shop.cache.CacheClient;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.ShopCustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ShopCustomerServiceImpl implements ShopCustomerService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Override
    public Page<Shop> pageShopsNearby(Double longitude, Double latitude, Double radius, String category, Integer page, Integer pageSize) {
        // 1. 确定定位 Key (有无分类过滤)
        String key;
        if (category != null && !category.trim().isEmpty()) {
            key = "o2o:shop:geo:category:" + category.trim();
        } else {
            key = "o2o:shop:geo:all";
        }

        // 2. 计算最大拉取数量 (limit = page * pageSize)
        int maxLimit = page * pageSize;

        // 3. 构造 GeoSearch 查询参数 (包含距离，距离由近到远升序，设置数量限制)
        RedisGeoCommands.GeoSearchCommandArgs args = RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                .includeDistance()
                .sortAscending()
                .limit(maxLimit);

        // 4. 执行 Redis GEO 检索
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radius, Metrics.KILOMETERS),
                args
        );

        if (results == null) {
            return new Page<>(page, pageSize, 0);
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        int total = content.size();

        // 5. 内存截取分页
        int start = (page - 1) * pageSize;
        if (start >= total) {
            return new Page<>(page, pageSize, total);
        }
        int end = Math.min(page * pageSize, total);

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> subList = content.subList(start, end);

        // 6. 装配店铺详情，回填动态距离
        List<Shop> shops = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : subList) {
            String shopIdStr = item.getContent().getName();
            Long shopId = Long.valueOf(shopIdStr);
            // distance.getValue() 拿到的是 KILOMETERS, 乘以 1000 转换为米
            double distanceInMeters = item.getDistance().getValue() * 1000.0;

            try {
                Shop shop = getShopById(shopId);
                if (shop != null) {
                    shop.setDistance(distanceInMeters);
                    shops.add(shop);
                }
            } catch (Exception e) {
                // 容错：防止某个店铺详情获取异常导致整个列表报错崩溃
            }
        }

        Page<Shop> shopPage = new Page<>(page, pageSize, total);
        shopPage.setRecords(shops);
        return shopPage;
    }
}
