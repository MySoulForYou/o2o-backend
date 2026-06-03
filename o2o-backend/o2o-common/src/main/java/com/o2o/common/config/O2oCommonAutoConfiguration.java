package com.o2o.common.config;

import com.o2o.common.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 公共模块自动配置类，利用 Spring Boot SPI 机制实现跨微服务组件自动装配。
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class O2oCommonAutoConfiguration {
}
