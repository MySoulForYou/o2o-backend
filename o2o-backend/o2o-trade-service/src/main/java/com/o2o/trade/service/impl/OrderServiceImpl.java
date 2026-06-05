package com.o2o.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.api.*;
import com.o2o.common.BusinessException;
import com.o2o.common.Result;
import com.o2o.common.UserContext;
import com.o2o.trade.entity.Order;
import com.o2o.trade.entity.OrderItem;
import com.o2o.trade.entity.SeckillVoucher;
import com.o2o.trade.mapper.OrderItemMapper;
import com.o2o.trade.mapper.OrderMapper;
import com.o2o.trade.mapper.SeckillVoucherMapper;
import com.o2o.trade.mq.SeckillOrderMessage;
import com.o2o.trade.service.OrderService;
import com.o2o.trade.vo.OrderCreateVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private CartClient cartClient;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Redis Key 前缀定义
    private static final String SECKILL_STOCK_PREFIX = "o2o:seckill:stock:";
    private static final String SECKILL_BOUGHT_PREFIX = "o2o:seckill:bought:";
    private static final String SECKILL_ORDER_STATUS_PREFIX = "o2o:seckill:order:status:";

    // Lua 脚本定义 (保证原子库存扣减与限购限制)
    private static final String SECKILL_LUA_SCRIPT =
            "local stockKey = KEYS[1]\n" +
            "local boughtKey = KEYS[2]\n" +
            "local userId = ARGV[1]\n" +
            "local quantity = tonumber(ARGV[2])\n" +
            "local isBought = redis.call('sismember', boughtKey, userId)\n" +
            "if isBought == 1 then\n" +
            "    return -2\n" +
            "end\n" +
            "if redis.call('exists', stockKey) == 1 then\n" +
            "    local stock = tonumber(redis.call('get', stockKey))\n" +
            "    if stock >= quantity then\n" +
            "        redis.call('incrby', stockKey, -quantity)\n" +
            "        redis.call('sadd', boughtKey, userId)\n" +
            "        return 1\n" +
            "    else\n" +
            "        return 0\n" +
            "    end\n" +
            "else\n" +
            "    return -1\n" +
            "end";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setScriptText(SECKILL_LUA_SCRIPT);
        REDIS_SCRIPT.setResultType(Long.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateVo createVo) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 1. 获取购物车中已勾选的商品列表
        Result<List<CartVo>> cartResult = cartClient.getSelectedCartItems();
        if (cartResult == null || cartResult.getData() == null || cartResult.getData().isEmpty()) {
            throw new BusinessException("购物车中无选中的有效商品");
        }
        List<CartVo> selectedItems = cartResult.getData();

        // 2. 校验商品是否下架或异常，并计算总金额
        List<Long> goodsIds = selectedItems.stream().map(CartVo::getGoodsId).collect(Collectors.toList());
        Result<List<GoodsDto>> goodsResult = goodsClient.getGoodsByIds(goodsIds);
        if (goodsResult == null || goodsResult.getData() == null || goodsResult.getData().isEmpty()) {
            throw new BusinessException("无法获取最新的商品详情");
        }
        Map<Long, GoodsDto> goodsMap = goodsResult.getData().stream()
                .collect(Collectors.toMap(GoodsDto::getId, g -> g));

        // 3. 按店铺对购物车项进行分组建单（由于O2O订单是按店铺配送，这里支持按店铺拆单）
        Map<Long, List<CartVo>> shopItemsMap = selectedItems.stream()
                .collect(Collectors.groupingBy(CartVo::getShopId));

        List<GoodsDeductStockDto> deductStockList = new ArrayList<>();
        List<Order> createdOrders = new ArrayList<>();

        for (Map.Entry<Long, List<CartVo>> entry : shopItemsMap.entrySet()) {
            Long shopId = entry.getKey();
            List<CartVo> items = entry.getValue();

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();

            // 预生成订单主键（使用 MyBatis-Plus 内置 Snowflake）
            Long orderId = IdWorker.getId();
            String orderNo = "ORD" + orderId;

            for (CartVo item : items) {
                GoodsDto goodsDto = goodsMap.get(item.getGoodsId());
                if (goodsDto == null || goodsDto.getStatus() != 1) {
                    throw new BusinessException("商品已下架或不可用: " + item.getGoodsName());
                }

                // 物理库存校验
                if (goodsDto.getStock() < item.getQuantity()) {
                    throw new BusinessException("商品库存不足: " + item.getGoodsName());
                }

                // 累计总价
                BigDecimal itemPrice = goodsDto.getPrice();
                BigDecimal itemTotal = itemPrice.multiply(new BigDecimal(item.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                // 创建明细对象
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(orderId);
                orderItem.setGoodsId(item.getGoodsId());
                orderItem.setGoodsName(goodsDto.getName());
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(item.getQuantity());
                orderItem.setImage(goodsDto.getImage());
                orderItems.add(orderItem);

                // 收集要扣减的物理库存
                deductStockList.add(new GoodsDeductStockDto(item.getGoodsId(), item.getQuantity()));
            }

            // 保存订单主表
            Order order = new Order();
            order.setId(orderId);
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShopId(shopId);
            order.setTotalAmount(totalAmount);
            order.setActualAmount(totalAmount); // 无 AI 优惠，等额计算
            order.setStatus(0); // 待付款
            order.setReceiverName(createVo.getReceiverName());
            order.setReceiverPhone(createVo.getReceiverPhone());
            order.setReceiverAddress(createVo.getReceiverAddress());
            order.setOrderType(0); // 普通订单
            
            orderMapper.insert(order);

            // 保存订单子表明细
            for (OrderItem orderItem : orderItems) {
                orderItemMapper.insert(orderItem);
            }

            createdOrders.add(order);
        }

        // 4. 调用 Feign 批量扣减 o2o-shop-service 物理库存 (开启远程乐观锁扣减)
        Result<Boolean> deductResult = goodsClient.deductStock(deductStockList);
        if (deductResult == null || !Boolean.TRUE.equals(deductResult.getData())) {
            throw new BusinessException("扣减商品库存失败，下单终止");
        }

        // 5. 调用 Feign 清除当前已下单的购物车勾选商品项
        cartClient.deleteCartItems(goodsIds);

        log.info("普通订单创建成功, userId: {}, 订单数: {}", userId, createdOrders.size());
        
        // 返回首个生成的订单主键作为凭证（如果拆单的话，可以让前端根据需要重定向）
        return createdOrders.get(0).getId();
    }

    @Override
    public Long createSeckillOrder(Long voucherId) {
        if (voucherId == null) {
            throw new BusinessException("秒杀商品 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 1. Redis Lua 预扣库存与排重校验
        String stockKey = SECKILL_STOCK_PREFIX + voucherId;
        String boughtKey = SECKILL_BOUGHT_PREFIX + voucherId;

        Long executeResult = stringRedisTemplate.execute(
                REDIS_SCRIPT,
                Arrays.asList(stockKey, boughtKey),
                String.valueOf(userId),
                "1"
        );

        if (executeResult == null) {
            throw new BusinessException("秒杀抢购系统异常，请稍后再试");
        }

        if (executeResult == -1) {
            // Redis 中无库存 Key，需要查库预热
            preheatSeckillVoucher(voucherId);
            // 重新执行一次
            executeResult = stringRedisTemplate.execute(
                    REDIS_SCRIPT,
                    Arrays.asList(stockKey, boughtKey),
                    String.valueOf(userId),
                    "1"
            );
            if (executeResult == null || executeResult < 0) {
                throw new BusinessException(executeResult == -2 ? "您已经参与过抢购，每人限购一件" : "商品已售罄");
            }
        } else if (executeResult == 0) {
            throw new BusinessException("商品已售罄，下次早点来哦");
        } else if (executeResult == -2) {
            throw new BusinessException("您已经参与过此抢购活动，每人限购一件");
        }

        // 2. 预扣成功，生成 Snowflake 订单 ID 与排队状态
        Long orderId = IdWorker.getId();
        String statusKey = SECKILL_ORDER_STATUS_PREFIX + orderId;
        
        // 在 Redis 中标识该订单的状态为：9 (正在异步排队建单中)
        stringRedisTemplate.opsForValue().set(statusKey, "9", 10, TimeUnit.MINUTES);

        // 3. 查询秒杀代金券的秒杀价
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            // 回滚 Redis 库存
            rollbackRedisStock(voucherId, userId);
            throw new BusinessException("活动代金券不存在或已下架");
        }

        // 4. 发送异步消息到 RabbitMQ，进行排队建单
        try {
            SeckillOrderMessage message = new SeckillOrderMessage();
            message.setOrderId(orderId);
            message.setUserId(userId);
            message.setVoucherId(voucherId);
            message.setQuantity(1);
            message.setPrice(voucher.getVoucherPrice());

            String msgJson = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    com.o2o.trade.config.RabbitMqConfig.SECKILL_EXCHANGE,
                    com.o2o.trade.config.RabbitMqConfig.SECKILL_ROUTING_KEY,
                    msgJson
            );
            log.info("秒杀消息投递成功, userId: {}, orderId: {}, voucherId: {}", userId, orderId, voucherId);
        } catch (Exception e) {
            log.error("秒杀异步下单投递 RabbitMQ 失败, userId: {}, orderId: {}, error: ", userId, orderId, e);
            // 回滚库存
            rollbackRedisStock(voucherId, userId);
            stringRedisTemplate.delete(statusKey);
            throw new BusinessException("排队网络异常，请重试");
        }

        return orderId;
    }

    @Override
    public Integer getSeckillOrderStatus(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }

        String statusKey = SECKILL_ORDER_STATUS_PREFIX + orderId;
        String statusStr = stringRedisTemplate.opsForValue().get(statusKey);

        if (statusStr != null) {
            // 如果 Redis 中有状态且为 9（排队中）或 -2（库存不足建单失败），直接返回
            int statusVal = Integer.parseInt(statusStr);
            if (statusVal == 9 || statusVal == -2) {
                return statusVal;
            }
        }

        // 否则 fallback 查询 MySQL 订单表中的最终交易状态
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            return order.getStatus();
        }

        // 极少可能：如果缓存已过期且数据库查不到
        return -2; // 判定为建单失败
    }

    /**
     * 辅助方法：从数据库中将秒杀代金券库存预热到 Redis
     */
    private void preheatSeckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BusinessException("秒杀代金券活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getStartTime())) {
            throw new BusinessException("秒杀活动尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            throw new BusinessException("秒杀活动已经结束");
        }

        String stockKey = SECKILL_STOCK_PREFIX + voucherId;
        // 如果键不存在，则回写，并设置过期时间为活动结束剩余秒数加 2 小时
        long expireSeconds = java.time.Duration.between(now, voucher.getEndTime()).toSeconds() + 7200;
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(voucher.getStock()), expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 辅助方法：回滚 Redis 虚拟库存与限购 Set 集合
     */
    private void rollbackRedisStock(Long voucherId, Long userId) {
        try {
            String stockKey = SECKILL_STOCK_PREFIX + voucherId;
            String boughtKey = SECKILL_BOUGHT_PREFIX + voucherId;

            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            log.info("Redis 虚拟库存回滚成功, voucherId: {}, userId: {}", voucherId, userId);
        } catch (Exception e) {
            log.error("Redis 虚拟库存回滚异常, voucherId: {}, userId: {}, error: ", voucherId, userId, e);
        }
    }
}
