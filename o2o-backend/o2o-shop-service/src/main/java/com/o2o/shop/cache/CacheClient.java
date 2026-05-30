package com.o2o.shop.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // 自定义后台线程池：专用于逻辑过期时，异步调用数据库重建缓存，防止业务线程受阻
    private final ExecutorService executorService = new ThreadPoolExecutor(
            4,                          // 核心线程数
            16,                         // 最大线程数
            60L, TimeUnit.SECONDS,     // 线程闲置生存时间
            new ArrayBlockingQueue<>(500), // 任务阻塞队列
            new ThreadFactory() {       // 线程工厂，给线程命名，便于运维排查问题
                private int count = 1;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "o2o-cache-rebuild-thread-" + count++);
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：如果队列爆满，由调用者线程直接执行重建，防止丢任务
    );

    public CacheClient(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // 注册 JavaTime 模块以支持 LocalDateTime
    }

    /**
     * 基础写操作：将数据写入 Redis，并设置物理 TTL（附带 0~300秒 随机抖动防雪崩）
     * 对店铺不用，店铺逻辑过期 天然防御缓存雪崩
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(300); 
            long totalSeconds = unit.toSeconds(time) + jitter;
            
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, totalSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Redis写入失败，JSON序列化异常. Key: {}", key, e);
        }
    }

    /**
     * 基础写操作（带逻辑过期）
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plus(time, unit.toChronoUnit()));
        try {
            String json = objectMapper.writeValueAsString(redisData);
            stringRedisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            log.error("Redis逻辑写入失败，JSON序列化异常. Key: {}", key, e);
        }
    }

    /**
     * 核心方案三：防【缓存击穿 + 缓存穿透 + 缓存雪崩】的旁路缓存查询（使用分布式互斥锁同步重建）
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, TypeReference<R> typeRef, Function<ID, R> dbFallback, 
            Long time, TimeUnit unit, String lockKeyPrefix) {
        String key = keyPrefix + id;

        // 1. 尝试从 Redis 读取缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.trim().isEmpty()) {
            if ("null".equals(json)) {
                return null;
            }
            try {
                return objectMapper.readValue(json, typeRef);
            } catch (Exception e) {
                log.error("缓存解析失败. Key: {}", key, e);
            }
        }
        if (json != null) {
            return null; // 二次校验
        }

        // 2. 缓存未命中，尝试获取分布式互斥锁防击穿
        String lockKey = lockKeyPrefix + id;
        RLock lock = redissonClient.getLock(lockKey);
        R r = null;
        try {
            // 尝试获取锁，最大等待时间 3 秒。
            // 【看门狗启用】这里不填 leaseTime（释放时间），Redisson 会自动启动 Watchdog 续期机制。
            // 重建线程在查库期间，看门狗会每 10 秒自动为锁续期，彻底防范慢查询导致锁提前失效引发的缓存踩踏/击穿。
            // 若服务崩溃，看门狗停止续期，Redis 锁也将在 30 秒内自动过期释放，绝无死锁风险。
            if (lock.tryLock(3L, TimeUnit.SECONDS)) {
                // 3. 双重检查锁 (Double Check)：防前一秒刚有线程写完缓存，自己重复读库
                json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.trim().isEmpty()) {
                    if ("null".equals(json)) {
                        return null;
                    }
                    return objectMapper.readValue(json, typeRef);
                }

                // 4. 真正查库
                r = dbFallback.apply(id);
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "null", 2L, TimeUnit.MINUTES);
                    return null;
                }

                // 5. 写入缓存（自动加随机 TTL）
                this.set(key, r, time, unit);
            } else {
                // 6. 抢锁超时后的宏观避让与重试：
                // 【设计说明】
                // 1) 为什么不用原生 Redis SETNX 自旋？
                //    在前面的 tryLock(3s) 等待期间，线程已通过 Redisson 底层的 Pub/Sub 机制被操作系统优雅挂起（零 CPU / 零 Redis 压力），
                //    只有当整整等待了 3 秒钟依然没有抢到锁时，才会进入这个 else 超时分支。
                // 2) 这里的 sleep(50ms) + 递归重试起什么作用？
                //    这不是高频抢锁自旋。在此短暂睡眠 50ms 并重新调用本方法后，第一步会先读取 Redis 缓存。
                //    因为已经过了 3 秒，前一个抢到锁重建缓存的线程极大概率已经完成写入，此时便能直接命中缓存返回，避免重复抢锁或击穿数据库。
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, typeRef, dbFallback, time, unit, lockKeyPrefix);
            }
        } catch (InterruptedException e) {
            log.error("抢锁自旋等待中断异常", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("同步重建缓存异常", e);
            r = dbFallback.apply(id); // 降级读库
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock(); // 只能释放自己线程加的锁
            }
        }
        return r;
    }

    /**
     * 核心方案二：防【缓存击穿】查询（逻辑过期 + 异步重建，且支持缓存穿透防御）
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, 
            Long expireTime, TimeUnit unit, String lockKeyPrefix) {
        String key = keyPrefix + id;

        // 1. 从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        
        // 防缓存穿透校验：如果命中缓存空值标记，直接拦截返回 null
        if ("null".equals(json)) {
            return null;
        }
        
        // 2. 冷启动情况：如果 Redis 彻底为空（没有旧数据），必须通过互斥锁同步加载
        if (json == null || json.trim().isEmpty()) {
            return loadDataSynchronously(key, id, type, dbFallback, expireTime, unit, lockKeyPrefix);
        }

        // 3. 命中缓存，反序列化逻辑包装类
        RedisData redisData;
        try {
            redisData = objectMapper.readValue(json, RedisData.class);
        } catch (Exception e) {
            log.error("逻辑过期包装类反序列化失败. Key: {}", key, e);
            return dbFallback.apply(id); // 降级读库
        }

        // 4. 从包装类中提取真实的业务数据
        R r = objectMapper.convertValue(redisData.getData(), type);
        LocalDateTime logicalExpireTime = redisData.getExpireTime();

        // 5. 校验逻辑时间是否过期
        if (LocalDateTime.now().isBefore(logicalExpireTime)) {
            // 5.1 未逻辑过期，直接返回最新的缓存数据
            return r;
        }

        // 6. 已逻辑过期，尝试进行异步刷新
        String lockKey = lockKeyPrefix + id;
        RLock lock = redissonClient.getLock(lockKey);
        
        // 主线程双重检查过滤：如果锁已被持有，说明已有后台重建任务在运行，无需重复提交任务
        if (!lock.isLocked()) {
            // 6.1 交由后台线程池提交异步重建任务
            executorService.submit(() -> {
                RLock asyncLock = redissonClient.getLock(lockKey);
                // 在异步线程中尝试抢锁，确保只有一个异步任务在执行重建
                if (asyncLock.tryLock()) {
                    try {
                        // 再次双重检查 (Double Check)：检查是否在前一秒已被其他任务重建完成
                        String currentJson = stringRedisTemplate.opsForValue().get(key);
                        if (currentJson != null && !currentJson.trim().isEmpty() && !"null".equals(currentJson)) {
                            RedisData currentRedisData = objectMapper.readValue(currentJson, RedisData.class);
                            if (LocalDateTime.now().isBefore(currentRedisData.getExpireTime())) {
                                return; // 已经被其他并发任务重建过了，直接退出
                            }
                        }
                        
                        // 查询最新数据库数据
                        R dbData = dbFallback.apply(id);
                        if (dbData == null) {
                            // 数据库中无此数据，防穿透写入空对象
                            stringRedisTemplate.opsForValue().set(key, "null", 2L, TimeUnit.MINUTES);
                        } else {
                            // 重新封装写入，更新逻辑时间，写回 Redis
                            this.setWithLogicalExpire(key, dbData, expireTime, unit);
                        }
                        log.info("[Cache Rebuild] 异步缓存刷新成功. Key: {}", key);
                    } catch (Exception e) {
                        log.error("异步刷新缓存异常. Key: {}", key, e);
                    } finally {
                        asyncLock.unlock(); // 在加锁的异步线程中释放锁，100% 安全
                    }
                }
            });
        }

        // 6.2 抢锁失败或已提交异步刷新，都直接返回老数据，用户完全无感
        return r;
    }

    /**
     * 同步加载方法：专用于解决冷启动时的并发请求同步等待（含缓存穿透拦截）
     */
    private <R, ID> R loadDataSynchronously(
            String key, ID id, Class<R> type, Function<ID, R> dbFallback, 
            Long expireTime, TimeUnit unit, String lockKeyPrefix) {
        String lockKey = lockKeyPrefix + id;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 5 秒。
            // 【看门狗启用】不填 leaseTime 开启自动续期，防范冷启动同步加载阶段慢查询导致的锁提前失效。
            if (lock.tryLock(5L, TimeUnit.SECONDS)) {
                // Double Check (双重校验)
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.trim().isEmpty()) {
                    if ("null".equals(json)) {
                        return null;
                    }
                    RedisData redisData = objectMapper.readValue(json, RedisData.class);
                    return objectMapper.convertValue(redisData.getData(), type);
                }

                // 确认无缓存，同步查库
                R dbData = dbFallback.apply(id);
                if (dbData == null) {
                    // 数据库也无数据，写入短期空值缓存，防穿透
                    stringRedisTemplate.opsForValue().set(key, "null", 2L, TimeUnit.MINUTES);
                    return null;
                }

                // 进行热点逻辑过期缓存预热
                this.setWithLogicalExpire(key, dbData, expireTime, unit);
                return dbData;
            }
        } catch (Exception e) {
            log.error("冷启动同步加载异常. Key: {}", key, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock(); // 释放锁
            }
        }
        return dbFallback.apply(id);
    }
}