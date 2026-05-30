package com.o2o.shop.service.merchant.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.common.BusinessException;
import com.o2o.common.UserContext;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.merchant.ShopMerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopMerchantServiceImpl implements ShopMerchantService {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createShop(Shop shop) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        // 1. 业务层前置校验：校验当前商家是否已经拥有店铺
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getOwnerId, currentUserId);
        if (shopMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(400, "当前商家账号已绑定过店铺，无法重复创建");
        }

        // 2. 绑定店铺所属的所有者
        shop.setOwnerId(currentUserId);
        if (shop.getStatus() == null) {
            shop.setStatus(1); // 默认营业状态为 1-营业中
        }

        try {
            shopMapper.insert(shop);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 3. 数据库底层唯一约束兜底，防范高并发重试/并发击穿绕过 selectCount 校验导致的脏数据
            throw new BusinessException(400, "当前商家账号已绑定过店铺，请勿重复创建");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Shop shop) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        if (shop.getId() == null) {
            throw new BusinessException("店铺 ID 不能为空");
        }

        // 校验目标店铺是否存在
        Shop existingShop = shopMapper.selectById(shop.getId());
        if (existingShop == null) {
            throw new BusinessException(404, "目标店铺不存在");
        }

        // 强校验越权行为：只能修改自己名下的店铺
        if (!existingShop.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(403, "越权操作：您无权进行此项配置");
        }

        // 禁止通过此接口更新 ownerId 属性，以防所有权漂移
        shop.setOwnerId(null);
        shopMapper.updateById(shop);

        // 主动失效 Redis 店铺详情缓存，防止 C 端读取脏数据
        stringRedisTemplate.delete("o2o:shop:detail:" + existingShop.getId());
    }

    @Override
    public Shop getMyShop() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }

        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getOwnerId, currentUserId).last("LIMIT 1");
        Shop shop = shopMapper.selectOne(queryWrapper);
        if (shop == null) {
            throw new BusinessException(404, "当前商家账号未绑定任何店铺");
        }
        return shop;
    }
}
