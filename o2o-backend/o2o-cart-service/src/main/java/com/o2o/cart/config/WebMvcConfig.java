package com.o2o.cart.config;

import com.o2o.common.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册上下文拦截器，获取从网关透传的 X-User-Id
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/**");
    }
}
