package com.o2o.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o2o.common.BusinessException;
import com.o2o.common.UserContext;
import com.o2o.trade.entity.SeckillVoucher;
import com.o2o.trade.mapper.SeckillVoucherMapper;
import com.o2o.trade.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private SeckillVoucherMapper seckillVoucherMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        UserContext.setUserId(12345L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() throws Exception {
        UserContext.clear();
        closeable.close();
    }

    @Test
    void testCreateSeckillOrder_Success() throws Exception {
        Long voucherId = 99L;
        
        // 模拟 Redis 执行 Lua 脚本返回 1 (库存扣减成功)
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        // 模拟数据库查询代金券
        SeckillVoucher voucher = new SeckillVoucher();
        voucher.setId(voucherId);
        voucher.setVoucherPrice(new BigDecimal("1.00"));
        voucher.setStock(10);
        voucher.setStartTime(LocalDateTime.now().minusHours(1));
        voucher.setEndTime(LocalDateTime.now().plusHours(1));
        when(seckillVoucherMapper.selectById(voucherId)).thenReturn(voucher);

        // 模拟 JSON 序列化
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // 执行秒杀下单
        Long orderId = orderService.createSeckillOrder(voucherId);

        // 校验订单 ID 非空且成功发送消息到 RabbitMQ
        assertNotNull(orderId);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("seckill.order.exchange"),
                eq("seckill.order.routing.key"),
                anyString()
        );
    }

    @Test
    void testCreateSeckillOrder_AlreadyBought() {
        Long voucherId = 99L;
        
        // 模拟 Redis 执行 Lua 脚本返回 -2 (限购超限)
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-2L);

        // 执行并验证抛出限购异常
        assertThrows(BusinessException.class, () -> orderService.createSeckillOrder(voucherId));
    }
}
