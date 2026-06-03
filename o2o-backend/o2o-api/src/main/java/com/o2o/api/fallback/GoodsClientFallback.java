package com.o2o.api.fallback;

import com.o2o.api.GoodsClient;
import com.o2o.api.GoodsDto;
import com.o2o.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * GoodsClient Feign 降级兜底实现
 */
@Component
public class GoodsClientFallback implements GoodsClient {

    private static final Logger log = LoggerFactory.getLogger(GoodsClientFallback.class);

    @Override
    public Result<List<GoodsDto>> getGoodsByIds(List<Long> ids) {
        log.warn("[Fallback] 店铺商品微服务异常或超时，批量获取商品信息自动降级为空列表。ids: {}", ids);
        return Result.ok(Collections.emptyList());
    }
}
