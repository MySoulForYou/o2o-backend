package com.o2o.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.api.GoodsClient;
import com.o2o.api.GoodsDto;
import com.o2o.common.Result;
import com.o2o.trade.entity.Order;
import com.o2o.trade.entity.OrderItem;
import com.o2o.trade.mapper.OrderItemMapper;
import com.o2o.trade.mapper.OrderMapper;
import com.o2o.trade.mapper.SeckillVoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SeckillOrderListener {

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SECKILL_STOCK_PREFIX = "o2o:seckill:stock:";
    private static final String SECKILL_BOUGHT_PREFIX = "o2o:seckill:bought:";
    private static final String SECKILL_ORDER_STATUS_PREFIX = "o2o:seckill:order:status:";

    @RabbitListener(queues = com.o2o.trade.config.RabbitMqConfig.SECKILL_QUEUE)
    public void handleSeckillOrderMessage(String messageContent) {
        log.info("接收到秒杀异步建单消息: {}", messageContent);
        SeckillOrderMessage message = null;
        try {
            message = objectMapper.readValue(messageContent, SeckillOrderMessage.class);
        } catch (Exception e) {
            log.error("反序列化秒杀下单消息失败, content: {}, error: ", messageContent, e);
            return; // 消息格式错误，直接丢弃，不进行重试
        }

        Long orderId = message.getOrderId();
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        String statusKey = SECKILL_ORDER_STATUS_PREFIX + orderId;

        try {
            // 执行本地数据库事务建单
            createOrderInTransaction(message);
            // 建单成功，删除 Redis 临时排队状态（前端轮询时查不到缓存，将直接穿透查 MySQL，从而获取到 0-待付款 最终状态）
            stringRedisTemplate.delete(statusKey);
            log.info("秒杀订单落库成功, orderId: {}, userId: {}", orderId, userId);
        } catch (Exception e) {
            log.error("秒杀订单落库失败, orderId: {}, userId: {}, 将执行回滚补偿, error: ", orderId, userId, e);
            // 补偿回滚 Redis 虚拟库存与限购状态
            rollbackRedisStock(voucherId, userId);
            // 标记 Redis 中订单状态为建单失败 (-2)
            stringRedisTemplate.opsForValue().set(statusKey, "-2", 10, java.util.concurrent.TimeUnit.MINUTES);
        }
    }

    /**
     * 声明式本地事务：扣减物理库存、远程获取商品名、写入订单表及订单明细表
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderInTransaction(SeckillOrderMessage message) {
        // 1. 扣减本地秒杀代金券物理库存 (乐观锁扣减)
        int updated = seckillVoucherMapper.deductStock(message.getVoucherId(), message.getQuantity());
        if (updated <= 0) {
            log.warn("扣减秒杀代金券物理库存失败，商品已无货. voucherId: {}", message.getVoucherId());
            throw new RuntimeException("Voucher stock run out");
        }

        // 2. 远程调用商品微服务获取代金券商品的基本详情 (如商品名、主图、店铺ID)
        String goodsName = "秒杀优惠代金券";
        String goodsImage = null;
        Long shopId = 0L;

        try {
            Result<List<GoodsDto>> goodsResult = goodsClient.getGoodsByIds(Collections.singletonList(message.getVoucherId()));
            if (goodsResult != null && goodsResult.getData() != null && !goodsResult.getData().isEmpty()) {
                GoodsDto goodsDto = goodsResult.getData().get(0);
                goodsName = goodsDto.getName();
                goodsImage = goodsDto.getImage();
                shopId = goodsDto.getShopId();
            }
        } catch (Exception e) {
            log.error("秒杀消费者远程调用商品微服务获取详情失败, 自动降级使用默认元数据. goodsId: {}, error: ", message.getVoucherId(), e);
        }

        // 3. 写入订单主表
        Order order = new Order();
        order.setId(message.getOrderId());
        order.setOrderNo("SEC" + message.getOrderId());
        order.setUserId(message.getUserId());
        order.setShopId(shopId);
        order.setTotalAmount(message.getPrice());
        order.setActualAmount(message.getPrice());
        order.setStatus(0); // 待付款
        order.setOrderType(1); // 秒杀订单
        order.setReceiverName("秒杀用户");
        order.setReceiverPhone("");
        order.setReceiverAddress("同城自提/在线派发");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);

        // 4. 写入订单明细表
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(message.getOrderId());
        orderItem.setGoodsId(message.getVoucherId());
        orderItem.setGoodsName(goodsName);
        orderItem.setPrice(message.getPrice());
        orderItem.setQuantity(message.getQuantity());
        orderItem.setImage(goodsImage);
        orderItem.setCreateTime(LocalDateTime.now());
        orderItem.setUpdateTime(LocalDateTime.now());

        orderItemMapper.insert(orderItem);
    }

    /**
     * 回滚 Redis 虚拟库存与限购 Set 集合
     */
    private void rollbackRedisStock(Long voucherId, Long userId) {
        try {
            String stockKey = SECKILL_STOCK_PREFIX + voucherId;
            String boughtKey = SECKILL_BOUGHT_PREFIX + voucherId;

            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
            log.info("消费者回滚 Redis 虚拟库存成功, voucherId: {}, userId: {}", voucherId, userId);
        } catch (Exception e) {
            log.error("消费者回滚 Redis 虚拟库存发生异常, voucherId: {}, userId: {}, error: ", voucherId, userId, e);
        }
    }
}
