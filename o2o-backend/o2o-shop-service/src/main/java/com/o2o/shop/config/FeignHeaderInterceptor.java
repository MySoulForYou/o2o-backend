package com.o2o.shop.config;

import com.o2o.common.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 从当前 ThreadLocal 线程上下文中提取网关透传的当前登录用户 ID
        Long userId = UserContext.getUserId();
        
        if (userId != null) {
            // 自动注入到 Feign 的 Outgoing 请求头中，向下游微服务隐式透传
            template.header("X-User-Id", String.valueOf(userId));
        }
    }
}
