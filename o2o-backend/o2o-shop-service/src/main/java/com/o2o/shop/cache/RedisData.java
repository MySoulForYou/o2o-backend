package com.o2o.shop.cache;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RedisData {
    private LocalDateTime expireTime; // 逻辑过期时间
    private Object data;              // 存放的真实业务数据
}