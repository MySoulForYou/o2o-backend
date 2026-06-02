package com.o2o.shop.service.merchant.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.common.BusinessException;
import com.o2o.common.UserContext;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.merchant.ShopMerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
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

        // 4. 注册事务同步：事务提交后写入 Redis GEO
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncRedisGeo(null, shop);
                }
            });
        } else {
            syncRedisGeo(null, shop);
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

        // 获取数据库更新后的完整店铺数据
        Shop finalShop = shopMapper.selectById(shop.getId());

        // 注册事务同步：事务提交后同步 GEO 并删除详情缓存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncRedisGeo(existingShop, finalShop);
                    stringRedisTemplate.delete("o2o:shop:detail:" + finalShop.getId());
                }
            });
        } else {
            syncRedisGeo(existingShop, finalShop);
            stringRedisTemplate.delete("o2o:shop:detail:" + finalShop.getId());
        }
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

    /**
     * 事务提交后的 Redis GEO 双写同步辅助逻辑
     */
    private void syncRedisGeo(Shop oldShop, Shop newShop) {
        try {
            String allKey = "o2o:shop:geo:all";
            String shopIdStr = newShop.getId().toString();

            // 1. 如果新状态是关闭/休息（status = 0）
            if (newShop.getStatus() == 0) {
                stringRedisTemplate.opsForGeo().remove(allKey, shopIdStr);
                if (newShop.getCategory() != null) {
                    stringRedisTemplate.opsForGeo().remove("o2o:shop:geo:category:" + newShop.getCategory(), shopIdStr);
                }
                if (oldShop != null && oldShop.getCategory() != null) {
                    stringRedisTemplate.opsForGeo().remove("o2o:shop:geo:category:" + oldShop.getCategory(), shopIdStr);
                }
                log.info("[GEO Sync] 店铺下架/休息，已从 GEO 移除. ShopId: {}", shopIdStr);
                return;
            }

            // 2. 如果新状态是营业（status = 1）
            if (newShop.getStatus() == 1) {
                if (newShop.getLongitude() == null || newShop.getLatitude() == null) {
                    return;
                }
                Point newPoint = new Point(newShop.getLongitude().doubleValue(), newShop.getLatitude().doubleValue());

                boolean isNew = oldShop == null;
                boolean statusChanged = oldShop != null && oldShop.getStatus() == 0;
                boolean locationChanged = oldShop != null && (
                        oldShop.getLongitude().compareTo(newShop.getLongitude()) != 0 ||
                        oldShop.getLatitude().compareTo(newShop.getLatitude()) != 0
                );

                if (isNew || statusChanged || locationChanged) {
                    stringRedisTemplate.opsForGeo().add(allKey, newPoint, shopIdStr);
                }

                // 处理分类 GEO Key
                String newCategoryKey = "o2o:shop:geo:category:" + newShop.getCategory();
                if (isNew) {
                    stringRedisTemplate.opsForGeo().add(newCategoryKey, newPoint, shopIdStr);
                } else {
                    String oldCategoryKey = "o2o:shop:geo:category:" + oldShop.getCategory();
                    boolean categoryChanged = !oldShop.getCategory().equals(newShop.getCategory());

                    if (categoryChanged) {
                        // 分类变了，从旧分类移除，加入新分类
                        stringRedisTemplate.opsForGeo().remove(oldCategoryKey, shopIdStr);
                        stringRedisTemplate.opsForGeo().add(newCategoryKey, newPoint, shopIdStr);
                    } else if (statusChanged || locationChanged) {
                        // 分类没变，状态或位置变了，更新新分类
                        stringRedisTemplate.opsForGeo().add(newCategoryKey, newPoint, shopIdStr);
                    }
                }
                log.info("[GEO Sync] 店铺上架/营业，GEO 同步成功. ShopId: {}", shopIdStr);
            }
        } catch (Exception e) {
            log.error("[GEO Sync] GEO 缓存同步失败，ShopId: {}", newShop.getId(), e);
        }
    }
}
