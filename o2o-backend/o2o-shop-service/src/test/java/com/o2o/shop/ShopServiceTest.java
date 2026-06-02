package com.o2o.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.o2o.common.BusinessException;
import com.o2o.common.UserContext;
import com.o2o.shop.entity.Goods;
import com.o2o.shop.entity.Review;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.GoodsMapper;
import com.o2o.shop.mapper.ReviewMapper;
import com.o2o.shop.mapper.ShopMapper;
import com.o2o.shop.service.customer.ShopCustomerService;
import com.o2o.shop.service.customer.ReviewCustomerService;
import com.o2o.shop.service.merchant.ShopMerchantService;
import com.o2o.shop.vo.ReviewVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.o2o.api.OrderClient;
import com.o2o.api.UserClient;
import com.o2o.common.Result;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
public class ShopServiceTest {

    @Autowired
    private ShopMerchantService shopMerchantService;

    @Autowired
    private ShopCustomerService shopCustomerService;

    @Autowired
    private ReviewCustomerService reviewCustomerService;

    @Autowired
    private com.o2o.shop.service.merchant.GoodsMerchantService goodsMerchantService;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private UserClient userClient;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    private com.o2o.shop.config.ShopGeoPreheatRunner shopGeoPreheatRunner;

    private Long testUserId = 9999L;
    private Long otherUserId = 8888L;
    private Shop testShop;
    private Goods testGoods;

    @BeforeEach
    public void setup() {
        UserContext.setUserId(testUserId);
        
        when(orderClient.verifyOrderForReview(any(), any())).thenReturn(Result.ok(true));
        
        // Mock 批量用户查询，返回测试用户的昵称
        java.util.List<com.o2o.api.UserDto> mockUsers = new java.util.ArrayList<>();
        com.o2o.api.UserDto userDto = new com.o2o.api.UserDto();
        userDto.setId(testUserId);
        userDto.setUsername("test_user");
        userDto.setNickname("真实昵称");
        mockUsers.add(userDto);
        when(userClient.getUserByIds(any())).thenReturn(Result.ok(mockUsers));

        // 清理当前测试用户的店铺历史数据
        shopMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop>().eq(Shop::getOwnerId, testUserId));

        // 构造测试店铺
        testShop = new Shop();
        testShop.setName("测试小店_" + UUID.randomUUID().toString().substring(0, 5));
        testShop.setCategory("数码");
        testShop.setAddress("测试地址");
        testShop.setLongitude(new BigDecimal("116.1234567"));
        testShop.setLatitude(new BigDecimal("39.1234567"));
        
        shopMerchantService.createShop(testShop);
        
        // 构造测试商品，用于评价关联
        testGoods = new Goods();
        testGoods.setShopId(testShop.getId());
        testGoods.setName("测试商品");
        testGoods.setPrice(new BigDecimal("99.00"));
        testGoods.setStock(10);
        testGoods.setStatus(1);
        goodsMapper.insert(testGoods);
    }

    @AfterEach
    public void tearDown() {
        // 清理测试数据
        if (testGoods != null && testGoods.getId() != null) {
            goodsMapper.deleteById(testGoods.getId());
            reviewMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review>().eq(Review::getGoodsId, testGoods.getId()));
        }
        if (testShop != null && testShop.getId() != null) {
            shopMapper.deleteById(testShop.getId());
        }
        UserContext.clear();
    }

    @Test
    public void testDuplicateShopCreationFails() {
        Shop anotherShop = new Shop();
        anotherShop.setName("另一个测试店铺");
        anotherShop.setCategory("美食");
        anotherShop.setAddress("另一个地址");
        anotherShop.setLongitude(new BigDecimal("116.1234567"));
        anotherShop.setLatitude(new BigDecimal("39.1234567"));

        // 一个商家重复创建店铺应抛出异常
        assertThrows(BusinessException.class, () -> shopMerchantService.createShop(anotherShop));
    }

    @Test
    public void testUpdateShopAndPreventHorizontalPrivilegeEscalation() {
        // 修改自己名下的店铺，应成功
        testShop.setName("更新后的店名");
        shopMerchantService.updateShop(testShop);
        
        Shop dbShop = shopCustomerService.getShopById(testShop.getId());
        assertEquals("更新后的店名", dbShop.getName());

        // 切换为其他商家 ID，尝试修改刚才的店铺，应拦截报错 (403)
        UserContext.setUserId(otherUserId);
        testShop.setName("越权修改的店名");
        assertThrows(BusinessException.class, () -> shopMerchantService.updateShop(testShop));
    }

    @Test
    public void testShopPagination() {
        Page<Shop> page = shopCustomerService.pageShops("数码", 1, 1, 10);
        assertNotNull(page);
        assertTrue(page.getRecords().size() > 0);
    }

    @Test
    public void testReviewCreationAndDuplicateGuard() {
        Review review = new Review();
        review.setOrderId(10086L);
        review.setGoodsId(testGoods.getId());
        review.setScore(5);
        review.setContent("非常不错！");
        review.setIsAnonymous(0);

        reviewCustomerService.addReview(review);

        // 分页获取评价列表，并验证默认昵称组装
        Page<ReviewVo> page = reviewCustomerService.pageReviewsByGoodsId(testGoods.getId(), 1, 10);
        assertTrue(page.getRecords().size() > 0);
        ReviewVo vo = page.getRecords().stream()
                .filter(r -> r.getOrderId().equals(10086L))
                .findFirst()
                .orElse(null);
        
        assertNotNull(vo);
        assertEquals("真实昵称", vo.getUsername());

        // 二次提交同一个订单同一单品的评价，应触发唯一索引联合约束防重，并友好报错
        Review duplicateReview = new Review();
        duplicateReview.setOrderId(10086L);
        duplicateReview.setGoodsId(testGoods.getId());
        duplicateReview.setScore(4);
        duplicateReview.setContent("再次评价");

        assertThrows(BusinessException.class, () -> reviewCustomerService.addReview(duplicateReview));
    }

    @Test
    public void testAnonymousReviewMasking() {
        Review review = new Review();
        review.setOrderId(10087L);
        review.setGoodsId(testGoods.getId());
        review.setScore(5);
        review.setContent("匿名好评！");
        review.setIsAnonymous(1);

        reviewCustomerService.addReview(review);

        Page<ReviewVo> page = reviewCustomerService.pageReviewsByGoodsId(testGoods.getId(), 1, 10);
        ReviewVo vo = page.getRecords().stream()
                .filter(r -> r.getOrderId().equals(10087L))
                .findFirst()
                .orElse(null);

        assertNotNull(vo);
        assertEquals("用***", vo.getUsername());
    }

    @Test
    public void testMerchantGetMyShop() {
        // 商家正常获取自己店铺
        Shop myShop = shopMerchantService.getMyShop();
        assertNotNull(myShop);
        assertEquals(testShop.getId(), myShop.getId());

        // 切换为无店铺的用户，获取时应抛出 404 异常
        UserContext.setUserId(otherUserId);
        assertThrows(BusinessException.class, () -> shopMerchantService.getMyShop());
    }

    @Test
    public void testMerchantUpdateGoodsAndPreventHorizontalPrivilegeEscalation() {
        // 商家修改自己店铺的商品，应成功
        testGoods.setName("升级版测试商品");
        testGoods.setPrice(new BigDecimal("199.00"));
        goodsMerchantService.updateGoods(testGoods);

        Goods dbGoods = goodsMapper.selectById(testGoods.getId());
        assertEquals("升级版测试商品", dbGoods.getName());
        assertEquals(new BigDecimal("199.00"), dbGoods.getPrice());

        // 切换为其他商家 ID，尝试修改该商品，应拦截报错 (403)
        UserContext.setUserId(otherUserId);
        testGoods.setName("恶意修改的商品");
        assertThrows(BusinessException.class, () -> goodsMerchantService.updateGoods(testGoods));
    }

    @Test
    public void testMerchantPageMyGoods() {
        // 商家分页查询自己店铺的商品
        Page<Goods> page = goodsMerchantService.pageMyGoods(1, 10);
        assertNotNull(page);
        assertEquals(1, page.getTotal());
        assertEquals(testGoods.getId(), page.getRecords().get(0).getId());

        // 切换为无店铺的用户，查询应报错
        UserContext.setUserId(otherUserId);
        assertThrows(BusinessException.class, () -> goodsMerchantService.pageMyGoods(1, 10));
    }

    @Test
    public void testLbsNearbySearchAndDoubleWrite() throws Exception {
        // 1. 手动运行预热，确保数据在 Redis 中
        shopGeoPreheatRunner.run();

        // 2. 验证预热的全局 GEO 中是否包含 testShop 的 ID
        String allKey = "o2o:shop:geo:all";
        java.util.List<org.springframework.data.geo.Point> position = stringRedisTemplate.opsForGeo().position(allKey, testShop.getId().toString());
        assertNotNull(position);
        assertFalse(position.isEmpty());
        assertNotNull(position.get(0));

        // 3. 验证附近的店铺检索，定位在稍微偏移一点的位置
        // testShop 坐标是 (116.1234567, 39.1234567)
        // 我们从 (116.123, 39.123) 检索附近 10.0 公里的“数码”店铺
        Page<Shop> nearbyShops = shopCustomerService.pageShopsNearby(116.123, 39.123, 10.0, "数码", 1, 10);
        assertNotNull(nearbyShops);
        assertTrue(nearbyShops.getRecords().size() > 0);
        Shop foundShop = nearbyShops.getRecords().stream()
                .filter(s -> s.getId().equals(testShop.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(foundShop);
        assertNotNull(foundShop.getDistance());
        assertTrue(foundShop.getDistance() > 0);

        // 4. 测试修改店铺分类时的双写一致性
        // 将分类修改为“美食”
        testShop.setCategory("美食");
        shopMerchantService.updateShop(testShop);

        // 验证旧分类 GEO key 中已不存在
        java.util.List<org.springframework.data.geo.Point> oldCatPos = stringRedisTemplate.opsForGeo().position("o2o:shop:geo:category:数码", testShop.getId().toString());
        assertTrue(oldCatPos == null || oldCatPos.isEmpty() || oldCatPos.get(0) == null);

        // 验证新分类 GEO key 中存在
        java.util.List<org.springframework.data.geo.Point> newCatPos = stringRedisTemplate.opsForGeo().position("o2o:shop:geo:category:美食", testShop.getId().toString());
        assertNotNull(newCatPos);
        assertFalse(newCatPos.isEmpty());
        assertNotNull(newCatPos.get(0));

        // 5. 测试关店下架时的双写一致性
        testShop.setStatus(0); // 关店
        shopMerchantService.updateShop(testShop);

        // 验证全局 GEO 和分类 GEO 中都已被移除
        java.util.List<org.springframework.data.geo.Point> allPosAfterClose = stringRedisTemplate.opsForGeo().position(allKey, testShop.getId().toString());
        assertTrue(allPosAfterClose == null || allPosAfterClose.isEmpty() || allPosAfterClose.get(0) == null);
    }
}
