package com.o2o.shop.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.o2o.shop.entity.Shop;
import com.o2o.shop.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ShopGeoPreheatRunner implements CommandLineRunner {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("====== [GEO Preheat] 开始预热营业中店铺地理位置索引 ======");
        try {
            // 1. 查询所有营业中的店铺
            LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Shop::getStatus, 1);
            List<Shop> activeShops = shopMapper.selectList(queryWrapper);
            if (activeShops == null || activeShops.isEmpty()) {
                log.info("====== [GEO Preheat] 未找到营业中的店铺，预热结束 ======");
                return;
            }

            // 2. 清理全局索引 Key 并重建
            String allKey = "o2o:shop:geo:all";
            stringRedisTemplate.delete(allKey);

            Map<String, Point> allMemberMap = new HashMap<>();
            for (Shop shop : activeShops) {
                if (shop.getLongitude() != null && shop.getLatitude() != null) {
                    allMemberMap.put(
                            shop.getId().toString(),
                            new Point(shop.getLongitude().doubleValue(), shop.getLatitude().doubleValue())
                    );
                }
            }

            if (!allMemberMap.isEmpty()) {
                stringRedisTemplate.opsForGeo().add(allKey, allMemberMap);
                log.info("====== [GEO Preheat] 全局 GEO 缓存预热成功，共加载 {} 家店铺 ======", allMemberMap.size());
            }

            // 3. 按分类分组清理并重建分类 GEO Key
            Map<String, List<Shop>> categoryGroup = activeShops.stream()
                    .filter(s -> s.getCategory() != null && !s.getCategory().trim().isEmpty())
                    .collect(Collectors.groupingBy(Shop::getCategory));

            for (Map.Entry<String, List<Shop>> entry : categoryGroup.entrySet()) {
                String category = entry.getKey();
                String categoryKey = "o2o:shop:geo:category:" + category;
                stringRedisTemplate.delete(categoryKey);

                Map<String, Point> catMemberMap = new HashMap<>();
                for (Shop shop : entry.getValue()) {
                    if (shop.getLongitude() != null && shop.getLatitude() != null) {
                        catMemberMap.put(
                                shop.getId().toString(),
                                new Point(shop.getLongitude().doubleValue(), shop.getLatitude().doubleValue())
                        );
                    }
                }

                if (!catMemberMap.isEmpty()) {
                    stringRedisTemplate.opsForGeo().add(categoryKey, catMemberMap);
                    log.info("====== [GEO Preheat] 分类 [{}] GEO 缓存预热成功，共加载 {} 家店铺 ======", category, catMemberMap.size());
                }
            }

            log.info("====== [GEO Preheat] 营业中店铺地理位置索引预热全部完成！ ======");
        } catch (Exception e) {
            log.error("====== [GEO Preheat] 预热失败，出现异常 ======", e);
        }
    }
}
