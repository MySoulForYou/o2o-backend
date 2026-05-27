package com.o2o.user.config;

import com.o2o.common.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 相关的拦截器、跨域等配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册用户上下文拦截器，拦截所有需要透传用户标识的接口
        registry.addInterceptor(new UserContextInterceptor())
                .addPathPatterns("/**");
    }
}
