package com.o2o.trade.config;

import com.o2o.common.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册用户上下文拦截器，从请求头读取网关透传的 X-User-Id 并放入 ThreadLocal
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/**");
    }
}
