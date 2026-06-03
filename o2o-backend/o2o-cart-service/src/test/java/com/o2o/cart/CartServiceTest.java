package com.o2o.cart;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
public class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private GoodsClient goodsClient;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final Long testUserId = 9999L;
    private final Long testGoodsId1 = 101L;
    private final Long testGoodsId2 = 102L;
    private final String redisKey = "o2o:cart:9999";

    @BeforeEach
    public void setup() {
        // 绑定上下文测试用户 ID
        UserContext.setUserId(testUserId);

        // 清理脏数据
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        stringRedisTemplate.delete(redisKey);

        // Mock Feign 批量商品查询接口
        List<GoodsDto> mockGoods = new ArrayList<>();
        
        GoodsDto g1 = new GoodsDto();
        g1.setId(testGoodsId1);
        g1.setShopId(1L);
        g1.setName("测试充电宝");
        g1.setPrice(new BigDecimal("15.50"));
        g1.setImage("http://cdn.o2o.com/charger.jpg");
        g1.setStatus(1);
        g1.setStock(50);
        mockGoods.add(g1);

        GoodsDto g2 = new GoodsDto();
        g2.setId(testGoodsId2);
        g2.setShopId(1L);
        g2.setName("已下架数据线");
        g2.setPrice(new BigDecimal("5.00"));
        g2.setImage("http://cdn.o2o.com/cable.jpg");
        g2.setStatus(0); // 下架
        g2.setStock(10);
        mockGoods.add(g2);

        when(goodsClient.getGoodsByIds(anyList())).thenReturn(Result.ok(mockGoods));
    }

    @AfterEach
    public void tearDown() {
        // 清理脏数据及上下文
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        stringRedisTemplate.delete(redisKey);
        UserContext.clear();
    }

    @Test
    public void testAddCartItemSuccess() {
        // 1. 初次加购商品 101 数量为 2
        cartService.addCartItem(testGoodsId1, 2);

        // 验证 Redis
        String redisVal = (String) stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1));
        assertEquals("2", redisVal);

        // 验证 MySQL
        Cart dbCart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertNotNull(dbCart);
        assertEquals(2, dbCart.getQuantity());

        // 2. 再次加购该商品数量为 3（应累加）
        cartService.addCartItem(testGoodsId1, 3);

        // 验证 Redis 和 MySQL 是否都累加为 5
        assertEquals("5", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        dbCart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertEquals(5, dbCart.getQuantity());
    }

    @Test
    public void testAddCartItemInvalidParams() {
        // 数量 <= 0 应当报错
        assertThrows(BusinessException.class, () -> cartService.addCartItem(testGoodsId1, 0));
        assertThrows(BusinessException.class, () -> cartService.addCartItem(testGoodsId1, -5));
        assertThrows(BusinessException.class, () -> cartService.addCartItem(null, 2));
    }

    @Test
    public void testUpdateCartItem() {
        // 1. 先加入
        cartService.addCartItem(testGoodsId1, 2);

        // 2. 覆盖数量为 10
        cartService.updateCartItem(testGoodsId1, 10);

        // 验证数量是否变为 10
        assertEquals("10", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        Cart dbCart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertEquals(10, dbCart.getQuantity());
    }

    @Test
    public void testDeleteCartItem() {
        // 1. 加购两个商品
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        // 2. 删除商品 101
        cartService.deleteCartItem(testGoodsId1);

        // 验证 Redis
        assertNull(stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        assertNotNull(stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId2)));

        // 验证 MySQL
        Long countG1 = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertEquals(0, countG1);

        Long countG2 = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId2));
        assertEquals(1, countG2);
    }

    @Test
    public void testClearCart() {
        // 1. 加购
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        // 2. 清空
        cartService.clearCart();

        // 验证 Redis & MySQL 均已为空
        assertFalse(stringRedisTemplate.hasKey(redisKey));
        Long count = cartMapper.selectCount(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        assertEquals(0, count);
    }

    @Test
    public void testGetCartListAndCacheAside() {
        // 1. 双写写入数据
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        // 2. 获取列表，并验证与 Feign DTO 的拼接
        List<CartVo> list = cartService.getCartList();
        assertNotNull(list);
        assertEquals(2, list.size());

        // 校验 101 号测试充电宝的字段匹配
        CartVo vo1 = list.stream().filter(v -> v.getGoodsId().equals(testGoodsId1)).findFirst().orElse(null);
        assertNotNull(vo1);
        assertEquals(2, vo1.getQuantity());
        assertEquals("测试充电宝", vo1.getGoodsName());
        assertEquals(new BigDecimal("15.50"), vo1.getPrice());
        assertEquals(1, vo1.getStatus());

        // 校验 102 号下架商品字段匹配
        CartVo vo2 = list.stream().filter(v -> v.getGoodsId().equals(testGoodsId2)).findFirst().orElse(null);
        assertNotNull(vo2);
        assertEquals(1, vo2.getQuantity());
        assertEquals("已下架数据线", vo2.getGoodsName());
        assertEquals(0, vo2.getStatus());

        // 3. 测试 Redis 缓存失效时从 DB 重建/预热缓存
        stringRedisTemplate.delete(redisKey); // 人为清理 Redis 缓存
        stringRedisTemplate.delete("o2o:cart:selected:9999");
        
        // 再次拉取，应触发 DB 查询并自动将数据预热回写 Redis
        List<CartVo> reloadList = cartService.getCartList();
        assertEquals(2, reloadList.size());
        
        // 验证缓存已被成功重建
        assertTrue(stringRedisTemplate.hasKey(redisKey));
        assertEquals("2", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        assertEquals("1", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId2)));
        assertTrue(stringRedisTemplate.hasKey("o2o:cart:selected:9999"));
    }

    @Test
    public void testUpdateSelectStatus() {
        // 1. 加购，默认均为勾选状态
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        String selectedKey = "o2o:cart:selected:9999";
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));

        // 2. 取消勾选商品 101
        cartService.updateSelectStatus(testGoodsId1, 0);

        // 校验 Redis
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));

        // 校验 MySQL
        Cart dbCart1 = cartMapper.selectOne(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId).eq(Cart::getGoodsId, testGoodsId1));
        assertEquals(0, dbCart1.getSelected());

        // 3. 全不选
        cartService.updateSelectStatus(null, 0);
        assertFalse(stringRedisTemplate.hasKey(selectedKey));
        List<Cart> list = cartMapper.selectList(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        for (Cart c : list) {
            assertEquals(0, c.getSelected());
        }

        // 4. 全选
        cartService.updateSelectStatus(null, 1);
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));
        list = cartMapper.selectList(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        for (Cart c : list) {
            assertEquals(1, c.getSelected());
        }
    }

    @Test
    public void testGetSelectedCartList() {
        // 1. 加购两个商品
        cartService.addCartItem(testGoodsId1, 2); // 选中, 上架
        cartService.addCartItem(testGoodsId2, 1); // 选中, 已下架(Mock status=0)

        // 2. 获取勾选且上架商品，应当只有 101（因为 102 虽选中但下架）
        List<CartVo> selectedItems = cartService.getSelectedCartList();
        assertNotNull(selectedItems);
        assertEquals(1, selectedItems.size());
        assertEquals(testGoodsId1, selectedItems.get(0).getGoodsId());

        // 3. 取消勾选 101，再次获取应为空
        cartService.updateSelectStatus(testGoodsId1, 0);
        selectedItems = cartService.getSelectedCartList();
        assertTrue(selectedItems.isEmpty());
    }

    @Test
    public void testDeleteCartItems() {
        // 1. 加购两个商品
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        String selectedKey = "o2o:cart:selected:9999";
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));

        // 2. 批量删除这两个商品
        List<Long> deleteIds = new ArrayList<>();
        deleteIds.add(testGoodsId1);
        deleteIds.add(testGoodsId2);
        cartService.deleteCartItems(deleteIds);

        // 验证 Redis
        assertNull(stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        assertNull(stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId2)));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));

        // 验证 MySQL
        Long count = cartMapper.selectCount(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, testUserId));
        assertEquals(0, count);
    }

    @Test
    public void testAddCartItemWithTransactionRollback() {
        // 开启事务
        org.springframework.transaction.TransactionStatus status = transactionManager.getTransaction(
                new org.springframework.transaction.support.DefaultTransactionDefinition());

        try {
            // 在事务中加购商品
            cartService.addCartItem(testGoodsId1, 5);

            // 验证在当前未提交的事务中，MySQL 应该已经能查询到了（在同一个会话中）
            Cart dbCart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, testUserId)
                    .eq(Cart::getGoodsId, testGoodsId1));
            assertNotNull(dbCart);
            assertEquals(5, dbCart.getQuantity());

            // 此时事务未提交，Redis 应该还没有被写入 (因为我们用了 afterCommit)
            String redisVal = (String) stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1));
            assertNull(redisVal);

            // 模拟事务回滚
            transactionManager.rollback(status);
        } catch (Exception e) {
            if (!status.isCompleted()) {
                transactionManager.rollback(status);
            }
            throw e;
        }

        // 事务回滚后，验证 MySQL 没有数据
        Cart dbCartAfterRollback = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertNull(dbCartAfterRollback);

        // 验证 Redis 也依然没有数据 (证明 afterCommit 没有触发)
        String redisValAfterRollback = (String) stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1));
        assertNull(redisValAfterRollback);
    }

    @Test
    public void testClearExpiredItems() {
        // 1. 加购两个商品
        // 根据 setup 中的 mock 逻辑：
        // testGoodsId1 (101) 的 status = 1 (正常商品)
        // testGoodsId2 (102) 的 status = 0 (下架商品)
        cartService.addCartItem(testGoodsId1, 2);
        cartService.addCartItem(testGoodsId2, 1);

        String selectedKey = "o2o:cart:selected:9999";
        // 确认初始状态
        assertEquals("2", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        assertEquals("1", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId2)));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));

        // 2. 调用清理失效商品接口
        cartService.clearExpiredItems();

        // 3. 验证 102 号已下架商品已被成功清除
        assertNull(stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId2)));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId2))));
        Long dbCountG2 = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId2));
        assertEquals(0, dbCountG2);

        // 4. 验证 101 号正常商品依旧留存，不受影响
        assertEquals("2", stringRedisTemplate.opsForHash().get(redisKey, String.valueOf(testGoodsId1)));
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(selectedKey, String.valueOf(testGoodsId1))));
        Cart dbCart1 = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, testUserId)
                .eq(Cart::getGoodsId, testGoodsId1));
        assertNotNull(dbCart1);
        assertEquals(2, dbCart1.getQuantity());
    }
}

