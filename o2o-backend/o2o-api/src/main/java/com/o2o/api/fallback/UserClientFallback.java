package com.o2o.api.fallback;

import com.o2o.api.UserClient;
import com.o2o.api.UserDto;
import com.o2o.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * UserClient Feign 降级兜底实现
 */
@Component
public class UserClientFallback implements UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClientFallback.class);

    @Override
    public Result<List<UserDto>> getUserByIds(List<Long> ids) {
        log.warn("[Fallback] 用户微服务异常或超时，批量获取用户信息自动降级为空列表。ids: {}", ids);
        // 返回空列表，以防下游接口挂掉时阻塞评价列表的加载
        return Result.ok(Collections.emptyList());
    }
}
