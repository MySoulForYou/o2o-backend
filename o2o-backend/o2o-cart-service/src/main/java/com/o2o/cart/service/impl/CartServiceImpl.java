package com.o2o.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.api.GoodsClient;
import com.o2o.api.GoodsDto;
import com.o2o.cart.entity.Cart;
import com.o2o.cart.mapper.CartMapper;
import com.o2o.cart.service.CartService;
import com.o2o.api.CartVo;
import com.o2o.common.BusinessException;
import com.o2o.common.Result;
import com.o2o.common.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {


    private static final String CART_KEY_PREFIX = "o2o:cart:";
    private static final long CART_TTL_DAYS = 30;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private GoodsClient goodsClient;

    /**
     * 辅助方法：仅在当前数据库事务提交成功后执行特定操作，如果当前没有活跃事务，则立即执行。
     */
    private void doAfterTransactionCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCartItem(Long goodsId, Integer quantity) {
        if (goodsId == null || quantity == null || quantity <= 0) {
            throw new BusinessException("参数错误，加购数量必须大于0");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;
        
        // 1. 写入 MySQL (同步双写，使用数据库唯一索引保障幂等)
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId).eq(Cart::getGoodsId, goodsId);
        Cart cart = cartMapper.selectOne(qw);
        
        if (cart != null) {
            cart.setQuantity(cart.getQuantity() + quantity);
            cart.setSelected(1); // 重新添加默认勾选
            cartMapper.updateById(cart);
        } else {
            cart = new Cart();
            cart.setUserId(userId);
            cart.setGoodsId(goodsId);
            cart.setQuantity(quantity);
            cart.setSelected(1); // 默认勾选
            cartMapper.insert(cart);
        }

        // 2. 事务提交成功后，写入 Redis (累加数量，并默认勾选)
        doAfterTransactionCommit(() -> {
            try {
                stringRedisTemplate.opsForHash().increment(cartKey, String.valueOf(goodsId), quantity);
                stringRedisTemplate.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
                
                stringRedisTemplate.opsForSet().add(selectedKey, String.valueOf(goodsId));
                stringRedisTemplate.expire(selectedKey, CART_TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("写入 Redis 购物车失败, userId: {}, goodsId: {}, error: ", userId, goodsId, e);
            }
        });

        log.info("加购成功, userId: {}, goodsId: {}, quantity: {}", userId, goodsId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCartItem(Long goodsId, Integer quantity) {
        if (goodsId == null || quantity == null || quantity <= 0) {
            throw new BusinessException("参数错误，商品数量必须大于0");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;

        // 1. 写入 MySQL
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId).eq(Cart::getGoodsId, goodsId);
        Cart cart = cartMapper.selectOne(qw);
        if (cart != null) {
            cart.setQuantity(quantity);
            cartMapper.updateById(cart);
        } else {
            cart = new Cart();
            cart.setUserId(userId);
            cart.setGoodsId(goodsId);
            cart.setQuantity(quantity);
            cartMapper.insert(cart);
        }

        // 2. 事务提交成功后，写入 Redis (覆盖数量)
        doAfterTransactionCommit(() -> {
            try {
                stringRedisTemplate.opsForHash().put(cartKey, String.valueOf(goodsId), String.valueOf(quantity));
                stringRedisTemplate.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                log.error("更新 Redis 购物车数量失败, userId: {}, goodsId: {}, error: ", userId, goodsId, e);
            }
        });

        log.info("更新购物车数量成功, userId: {}, goodsId: {}, quantity: {}", userId, goodsId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartItem(Long goodsId) {
        if (goodsId == null) {
            throw new BusinessException("商品 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;

        // 1. 从 MySQL 删除
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId).eq(Cart::getGoodsId, goodsId);
        cartMapper.delete(qw);

        // 2. 事务提交成功后，从 Redis 删除
        doAfterTransactionCommit(() -> {
            try {
                stringRedisTemplate.opsForHash().delete(cartKey, String.valueOf(goodsId));
                stringRedisTemplate.opsForSet().remove(selectedKey, String.valueOf(goodsId));
            } catch (Exception e) {
                log.error("从 Redis 删除购物车项失败, userId: {}, goodsId: {}, error: ", userId, goodsId, e);
            }
        });

        log.info("删除购物车项成功, userId: {}, goodsId: {}", userId, goodsId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;

        // 1. 从 MySQL 清空
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId);
        cartMapper.delete(qw);

        // 2. 事务提交成功后，从 Redis 清空
        doAfterTransactionCommit(() -> {
            try {
                stringRedisTemplate.delete(cartKey);
                stringRedisTemplate.delete(selectedKey);
            } catch (Exception e) {
                log.error("清空 Redis 购物车失败, userId: {}, error: ", userId, e);
            }
        });

    }

    @Override

    public List<CartVo> getCartList() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;

        // 1. 优先查 Redis Hash 缓存
        Map<Object, Object> redisCart = null;
        try {
            redisCart = stringRedisTemplate.opsForHash().entries(cartKey);
        } catch (Exception e) {
            log.error("查询 Redis 购物车失败, 将降级直查数据库, userId: {}, error: ", userId, e);
        }

        List<Cart> cartList;
        if (redisCart == null || redisCart.isEmpty()) {
            // 2. 缓存未命中，查 MySQL
            LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
            qw.eq(Cart::getUserId, userId);
            cartList = cartMapper.selectList(qw);

            if (cartList.isEmpty()) {
                return Collections.emptyList();
            }

            // 异步或同步预热回写 Redis Hash 和 Redis Set
            try {
                Map<String, String> tempMap = new HashMap<>();
                List<String> selectedGoodsIds = new ArrayList<>();
                for (Cart cart : cartList) {
                    tempMap.put(String.valueOf(cart.getGoodsId()), String.valueOf(cart.getQuantity()));
                    if (cart.getSelected() != null && cart.getSelected() == 1) {
                        selectedGoodsIds.add(String.valueOf(cart.getGoodsId()));
                    }
                }
                stringRedisTemplate.opsForHash().putAll(cartKey, tempMap);
                stringRedisTemplate.expire(cartKey, CART_TTL_DAYS, TimeUnit.DAYS);

                if (!selectedGoodsIds.isEmpty()) {
                    stringRedisTemplate.opsForSet().add(selectedKey, selectedGoodsIds.toArray(new String[0]));
                    stringRedisTemplate.expire(selectedKey, CART_TTL_DAYS, TimeUnit.DAYS);
                }
            } catch (Exception e) {
                log.error("回写 Redis 购物车及勾选缓存失败, userId: {}, error: ", userId, e);
            }
        } else {
            // 3. 缓存命中，为保证返回的购物车项包含数据库自增主键 ID，从数据库中拉取结构
            LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
            qw.eq(Cart::getUserId, userId);
            cartList = cartMapper.selectList(qw);
            
            // 读取 Redis 勾选缓存
            Set<String> selectedGoods = Collections.emptySet();
            try {
                selectedGoods = stringRedisTemplate.opsForSet().members(selectedKey);
                if (selectedGoods == null) {
                    selectedGoods = Collections.emptySet();
                }
            } catch (Exception e) {
                log.error("读取 Redis 购物车勾选状态失败, userId: {}, error: ", userId, e);
            }

            // 使用 Redis 中的最新数据覆盖本地 DB
            Map<Object, Object> finalRedisCart = redisCart;
            Set<String> finalSelectedGoods = selectedGoods;
            cartList.forEach(cart -> {
                String goodsIdStr = String.valueOf(cart.getGoodsId());
                if (finalRedisCart.containsKey(goodsIdStr)) {
                    try {
                        cart.setQuantity(Integer.parseInt((String) finalRedisCart.get(goodsIdStr)));
                    } catch (NumberFormatException e) {
                        log.error("解析 Redis 购物车数量失败, goodsId: {}, value: {}", cart.getGoodsId(), finalRedisCart.get(goodsIdStr));
                    }
                }
                // 使用 Redis 选中状态
                cart.setSelected(finalSelectedGoods.contains(goodsIdStr) ? 1 : 0);
            });
        }

        if (cartList.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 收集商品 ID 列表，通过 Feign 批量拉取商品详情
        List<Long> goodsIds = cartList.stream().map(Cart::getGoodsId).collect(Collectors.toList());
        Result<List<GoodsDto>> goodsResult = null;
        try {
            goodsResult = goodsClient.getGoodsByIds(goodsIds);
        } catch (Exception e) {
            log.error("远程调用商品服务获取详情失败, goodsIds: {}, error: ", goodsIds, e);
        }

        Map<Long, GoodsDto> goodsMap = new HashMap<>();
        if (goodsResult != null && goodsResult.getData() != null) {
            for (GoodsDto dto : goodsResult.getData()) {
                goodsMap.put(dto.getId(), dto);
            }
        }

        // 5. 内存组装 VO 列表
        List<CartVo> voList = new ArrayList<>();
        for (Cart cart : cartList) {
            CartVo vo = new CartVo();
            vo.setId(cart.getId());
            vo.setGoodsId(cart.getGoodsId());
            vo.setQuantity(cart.getQuantity());
            vo.setSelected(cart.getSelected());

            GoodsDto goodsDto = goodsMap.get(cart.getGoodsId());
            if (goodsDto != null) {
                vo.setGoodsName(goodsDto.getName());
                vo.setPrice(goodsDto.getPrice());
                vo.setImage(goodsDto.getImage());
                vo.setShopId(goodsDto.getShopId());
                vo.setStatus(goodsDto.getStatus());
            } else {
                // 如果商品在商品微服务中不存在（可能被物理删除）
                vo.setGoodsName("商品已失效");
                vo.setStatus(0); // 标记下架/失效
            }
            voList.add(vo);
        }

        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelectStatus(Long goodsId, Integer selected) {
        if (selected == null || (selected != 0 && selected != 1)) {
            throw new BusinessException("参数错误，勾选状态不合法");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;

        if (goodsId != null) {
            // 1. 更新 MySQL
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Cart> uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(Cart::getUserId, userId)
              .eq(Cart::getGoodsId, goodsId)
              .set(Cart::getSelected, selected);
            cartMapper.update(null, uw);

            // 2. 事务提交成功后，更新 Redis
            doAfterTransactionCommit(() -> {
                try {
                    if (selected == 1) {
                        stringRedisTemplate.opsForSet().add(selectedKey, String.valueOf(goodsId));
                    } else {
                        stringRedisTemplate.opsForSet().remove(selectedKey, String.valueOf(goodsId));
                    }
                    stringRedisTemplate.expire(selectedKey, CART_TTL_DAYS, TimeUnit.DAYS);
                } catch (Exception e) {
                    log.error("更新 Redis 购物车勾选状态失败, userId: {}, goodsId: {}, error: ", userId, goodsId, e);
                }
            });
        } else {
            // 1. 更新 MySQL
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Cart> uw = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            uw.eq(Cart::getUserId, userId)
              .set(Cart::getSelected, selected);
            cartMapper.update(null, uw);

            // 2. 事务提交成功后，更新 Redis
            doAfterTransactionCommit(() -> {
                try {
                    if (selected == 0) {
                        stringRedisTemplate.delete(selectedKey);
                    } else {
                        Set<Object> keys = stringRedisTemplate.opsForHash().keys(cartKey);
                        if (keys != null && !keys.isEmpty()) {
                            String[] goodsIdsArr = keys.stream().map(Object::toString).toArray(String[]::new);
                            stringRedisTemplate.opsForSet().add(selectedKey, goodsIdsArr);
                            stringRedisTemplate.expire(selectedKey, CART_TTL_DAYS, TimeUnit.DAYS);
                        }
                    }
                } catch (Exception e) {
                    log.error("更新 Redis 购物车全选状态失败, userId: {}, error: ", userId, e);
                }
            });
        }
    }

    @Override
    public List<CartVo> getSelectedCartList() {
        List<CartVo> allItems = getCartList();
        return allItems.stream()
                .filter(item -> item.getSelected() != null && item.getSelected() == 1 && item.getStatus() == 1)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartItems(List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            throw new BusinessException("商品 ID 列表不能为空");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        String cartKey = CART_KEY_PREFIX + userId;
        String selectedKey = "o2o:cart:selected:" + userId;

        // 1. 从 MySQL 批量删除对应记录
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId).in(Cart::getGoodsId, goodsIds);
        cartMapper.delete(qw);

        // 2. 事务提交成功后，从 Redis 批量删除商品项和选中状态
        doAfterTransactionCommit(() -> {
            try {
                Object[] fields = goodsIds.stream().map(String::valueOf).toArray(Object[]::new);
                stringRedisTemplate.opsForHash().delete(cartKey, fields);
                stringRedisTemplate.opsForSet().remove(selectedKey, fields);
            } catch (Exception e) {
                log.error("从 Redis 批量删除购物车项失败, userId: {}, goodsIds: {}, error: ", userId, goodsIds, e);
            }
        });
        
        log.info("批量删除购物车项成功, userId: {}, goodsIds: {}", userId, goodsIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearExpiredItems() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 1. 从 MySQL 查询当前用户购物车所有的商品 ID
        LambdaQueryWrapper<Cart> qw = new LambdaQueryWrapper<>();
        qw.eq(Cart::getUserId, userId).select(Cart::getGoodsId);
        List<Cart> carts = cartMapper.selectList(qw);
        if (carts == null || carts.isEmpty()) {
            return;
        }
        List<Long> goodsIds = carts.stream().map(Cart::getGoodsId).collect(Collectors.toList());

        // 2. 调用商品微服务批量查询商品详情
        List<GoodsDto> goodsList = null;
        try {
            Result<List<GoodsDto>> result = goodsClient.getGoodsByIds(goodsIds);
            if (result != null && result.getCode() == 200) {
                goodsList = result.getData();
            }
        } catch (Exception e) {
            log.error("调用商品微服务批量查询商品详情失败, userId: {}, goodsIds: {}, error: ", userId, goodsIds, e);
            throw new BusinessException("清理失效商品失败，商品服务不可用");
        }

        // 3. 找出所有失效商品的 ID (在商品微服务查不到，或者状态为下架 0)
        Set<Long> validGoodsIds = new HashSet<>();
        if (goodsList != null) {
            for (GoodsDto g : goodsList) {
                if (g.getStatus() != null && g.getStatus() == 1) {
                    validGoodsIds.add(g.getId());
                }
            }
        }

        List<Long> expiredGoodsIds = goodsIds.stream()
                .filter(id -> !validGoodsIds.contains(id))
                .collect(Collectors.toList());

        // 4. 执行批量删除
        if (!expiredGoodsIds.isEmpty()) {
            deleteCartItems(expiredGoodsIds);
            log.info("清理购物车失效商品成功, userId: {}, 清理商品数量: {}, goodsIds: {}", userId, expiredGoodsIds.size(), expiredGoodsIds);
        } else {
            log.info("未发现购物车中有失效商品, userId: {}", userId);
        }
    }
}

